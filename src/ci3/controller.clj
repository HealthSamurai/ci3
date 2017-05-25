(ns ci3.controller
  (:require [ci3.k8s :as k8s]
            [clojure.walk :as walk]))

(def cfg {:apiVersion "ci3.io/v1" :ns "default"})

(defonce stop (atom nil))

(defn process [builds]
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
                            :spec {:volumes
                                   [{:name "docker-sock"
                                     :hostPath {:path "/var/run/docker.sock"}}]
                                   :containers
                                   [{:name "agent"
                                     :image "aidbox/ci3:latest"
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
                              :status "scheduled"})))))))))


(defn watch []
  (if @stop
    (println "Stop watching")
    (future (process (walk/keywordize-keys (k8s/list k8s/cfg :builds)))
            (Thread/sleep 5000)
            (watch))))

(comment
  (watch)

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
