(ns ci3.build.core
  (:require [unifn.core :as u]))

(defmethod u/*fn
  :ci3.watch/build
  [{env :env res :resource}]
  (println "Start building " res))

#_(defn process-build [builds]
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
                                     :secret {:secretName "storage"
                                              :items [{:key "boto" :path ".boto"}]}}]
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
