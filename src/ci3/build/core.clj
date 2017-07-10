(ns ci3.build.core
  (:require [unifn.core :as u]))

(defn pod-spec [res]
  {:restartPolicy "Never"
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
     :env [{:name "BUILD_ID" :value (get-in res [:metadata :name])}
           {:name "BOTO_CONFIG" :value "/gsutil/.boto"}
           {:name "REPOSITORY" :value (get-in res [:repository :metadata :name])}
           {:name "DOCKER_KEY" :valueFrom {:secretKeyRef {:name "docker-registry" :key "key"}}}
           {:name "SERVICE_ACCOUNT" :valueFrom {:secretKeyRef {:name "docker-registry" :key "key"}}}]}]})

(defmethod u/*fn
  :ci3.watch/build
  [{env :env {{nm :name} :metadata :as res} :resource}]
  (println "Start building " res)
  {:fx {:k8s/create  {:resource :pods
                      :apiVersion "api/v1"
                      :ns "default"
                      :data {:apiVersion "v1"
                             :kind "Pod"
                             :metadata {:name name
                                        :annotations {:system "ci3"}
                                        :lables {:system "ci3"}}
                             :spec (pod-spec res)}}}})
#_(k8s/patch k8s/cfg :builds
           id {:pod pod
               :scheduledAt (str (java.util.Date.))
               :status "sche"})

