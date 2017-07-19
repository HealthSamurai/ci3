(ns ci3.build.core
  (:require [unifn.core :as u]
            [clojure.string :as str]
            [ci3.k8s :as k8s]
            [clojure.tools.logging :as log]))

(defn pod-spec [res]
  {:restartPolicy "Never"
   :volumes
   [{:name "docker-sock"
     :hostPath {:path "/var/run/docker.sock"}}
    {:name "gsutil"
     :secret {:secretName "gce"
              :items [{:key "boto" :path ".boto"}
                      {:key "account" :path "account.json"}]}}]
   :containers
   [{:name "agent"
     :image "eu.gcr.io/vivid-kite-171620/ci3:latest"
     :imagePullPolicy "Always"
     :args ["agent"]
     :volumeMounts
     [{:name "docker-sock"
       :mountPath "/var/run/docker.sock"}
      {:name "gsutil"
       :mountPath "/gsutil"
       :readOnly true}]
     :env
     [{:name "BUILD_ID" :value (get-in res [:metadata :name])}
      {:name "BOTO_CONFIG" :value "/gsutil/.boto"}
      {:name "REPOSITORY" :value (get-in res [:repository])}
      {:name "DOCKER_KEY" :valueFrom {:secretKeyRef {:name "docker-registry" :key "key"}}}
      {:name "SERVICE_ACCOUNT" :valueFrom {:secretKeyRef {:name "docker-registry" :key "key"}}}]}]})

(defmethod u/*fn
  ::build
  [{env :env cfg :k8s {{{nm :name} :metadata :as build}  :object tp :type } :resource}]
  (when (= tp "ADDED")
    (log/info "Create build pod #" nm)
    (let [pod (k8s/create cfg :pods
                          {:apiVersion "v1"
                           :kind "Pod"
                           :metadata {:name (str "build-" nm)
                                      :annotations {:system "ci3"}
                                      :lables {:system "ci3"}}
                           :spec (pod-spec build)})]
      {::pod pod})))
