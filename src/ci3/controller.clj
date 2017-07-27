(ns ci3.controller
  (:require [ci3.k8s :as k8s]
            [ci3.github :as gh]
            [clojure.walk :as walk]))

(def cfg {:apiVersion "ci3.io/v1" :ns "default"})

(defonce stop (atom nil))

(defn process-build [builds]
  (when-let [items (:items builds)]
    (doseq [i items]
      (when-not (:pod i)
        (let [id (get-in i [:metadata :name])]
          (println "Process " id)
          (let [pod (k8s/create
                     {:prefix "api" :ns "default" :apiVersion "v1"}
                     :pods {:apiVersion "v1"
                            :kind "Pod"
                            :metadata {:name id
                                       :annotations {:system "ci3"}
                                       :lables {:system "ci3"}}
                            :spec {:restartPolicy "Never"
                                   :volumes
                                   [{:name "docker-sock"
                                     :hostPath {:path "/var/run/docker.sock"}}
                                    {:name "gsutil"
                                     :secret {:secretName "boto"
                                              :items [{:key "boto" :path ".boto"}
                                                      {:key "account" :path "account.json"}]} }]
                                   :containers
                                   [{:name "agent"
                                     :image "eu.gcr.io/aidbox-next/ci3:latest"
                                     :imagePullPolicy "Always"
                                     :args ["agent"]
                                     :volumeMounts
                                     [{:name "docker-sock"
                                       :mountPath "/var/run/docker.sock"}
                                      {:name "gsutil"
                                       :mountPath "/gsutil"
                                       :readOnly true}]
                                     :env [{:name "BUILD_ID" :value id}
                                           {:name "BOTO_CONFIG" :value "/gsutil/.boto"}
                                           {:name "REPOSITORY" :value (get-in i [:payload :repository :full_name])}
                                           {:name "DOCKER_KEY" :valueFrom {:secretKeyRef {:name "docker-registry" :key "key"}}}
                                           {:name "SERVICE_ACCOUNT" :valueFrom {:secretKeyRef {:name "docker-registry" :key "key"}}}]}]}})]
            (println "Create pod" pod)
            (when-not (= "Failure" (get pod "status"))
              (println "Update build:"
                       (k8s/patch k8s/cfg :builds
                                  id {:pod pod
                                      :scheduledAt (str (java.util.Date.))
                                      :status "sche"})))))))))

(defn watch-resource [cfg rt process]
  (if @stop
    (println "Stop watching " (name rt))
    (future
      (process (walk/keywordize-keys (k8s/list cfg rt)))
      (Thread/sleep 5000)
      (watch-resource cfg rt process))))

(defn process-repository [repositories]
  (when-let  [repos (:items repositories)]
    (doseq [repo repos]
      (when-not (:hook repo)
        (let [id (get-in repo [:metadata :name])
              hook (gh/create-webhook repo)]
          (k8s/patch k8s/cfg :repositories id
                     {:hook hook}))))))

(defn watch []
  (watch-resource cfg :builds process-build)
  (watch-resource cfg :repositories process-repository))

(comment

  (watch-resource cfg :builds process-build)

  (watch-resource cfg :repositories process-repository)

  (doseq [b (get (k8s/list cfg :builds) "items")]
    (println
     (k8s/delete cfg :builds
                 (get-in b ["metadata" "name"]))))


  (reset! stop true)
  (reset! stop false)


  ;; (:items (walk/keywordize-keys (k8s/list k8s/cfg :builds)))

  ;; (process (walk/keywordize-keys (k8s/list k8s/cfg :builds)))

  ;; (map :pod (:items (walk/keywordize-keys (k8s/list k8s/cfg :builds))))

  ;; (k8s/list {:prefix "api" :ns "default" :apiVersion "v1"} :pods)


  #_(k8s/create k8s/cfg :builds
                {:kind "Build"
                 :apiVersion "ci3.io/v1"
                 :metadata {:name "ci3-build-6"}
                 :repository "https://github.com/healthsamurai/ci3"})

  )
