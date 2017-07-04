(ns ci3.fx
  (:require [cheshire.core :as json]
            [org.httpkit.client :as http]
            [unifn.core :as u]
            [ci3.k8s :as k8s]
            [clojure.tools.logging :as log]))

(defmethod u/*fn
  :http
  [args]
  (log/info "HTTP:" (:method args) (:url args) args)
  (let [res @(http/request (update args :body (fn [x] (when (and x (not (string? x))) (json/generate-string x)))))]
    (log/info "HTTP:" (:method args) (:url args) res)
    (if (and (:status res) (int? (:status res)) (< (:status res) 400))
      (-> res :body (json/parse-string keyword))
      {::u/status :error
       ::u/errors res
       ::u/message (str "Request failed with")})))

(defmethod u/*fn
  :k8s/patch
  [{id :id cfg :config rt :resource d :data}]
  (log/info "K8S patch:" cfg rt id d)
  (k8s/patch cfg rt id d))
