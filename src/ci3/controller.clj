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
                            :metadata {:name id :lables {:system "ci3"}}
                            :spec {:restartPolicy "Never"
                                   :volumes
                                   [{:name "docker-sock"
                                     :hostPath {:path "/var/run/docker.sock"}}]
                                   :containers
                                   [{:name "agent"
                                     :image "eu.gcr.io/aidbox-next/ci3:latest"
                                     :imagePullPolicy "Always"
                                     :args ["agent"]
                                     :volumeMounts
                                     [{:name "docker-sock"
                                       :mountPath "/var/run/docker.sock"}]
                                     :env [{:name "BUILD_ID" :value id}
                                           {:name "REPOSITORY" :value (get-in i [:payload :repository :url])}
                                           {:name "DOCKER_KEY" :valueFrom {:secretKeyRef {:name "docker-registry" :key "key"}}}
                                           {:name "SERVICE_ACCOUNT" :valueFrom {:secretKeyRef {:name "docker-registry" :key "key"}}}]}
                                    ]}})]
            (println "Create pod" pod)
            (when-not (= "Failure" (get pod "status"))
              (println "Update build:"
                       (k8s/patch k8s/cfg :builds
                          id {:pod pod
                              :scheduledAt (str (java.util.Date.))
                              :status "sche"})))))))))

(defn watch-resource [rt process]
  (if @stop
    (println "Stop watching " (name rt))
    (future (process (walk/keywordize-keys (k8s/list k8s/cfg rt)))
            (Thread/sleep 10000)
            (watch-resource rt process))))

(defn process-repository [repositories]
  (when-let  [repos (:items repositories)]
    (doseq [r repos]
      (when-not (:staus r)
        (println "Add webhook " (:url r))
        (spit "/tmp/repl" (str "add web hook" "\n\n\n") )
        )
      )
    ))

(defn restart []
  (do
    (reset! stop true)
    (reset! stop false)
    (watch-resource :repositories process-repository)))


(comment

  (watch-resource :build process-build)
  (watch-resource :repositories process-repository)

  (k8s/list k8s/cfg :repositories)
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
