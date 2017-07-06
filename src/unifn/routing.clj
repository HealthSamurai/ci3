(ns unifn.routing
  (:require [unifn.core :as u]
            [route-map.core :as route-map]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(s/def ::request-method keyword?)
(s/def ::uri string?)
(s/def ::request (s/keys :req-un [::request-method ::uri]))
(s/def ::routes (s/or :map map? :var var?))
(s/def ::cache (s/keys :req-un [::routes]))
(s/def ::dispatch (s/keys :req-un [::request ::cache]))

(defmethod u/*fn
  ::dispatch
  [{{routes :routes} :cache {uri :uri meth :request-method} :request :as arg}]
  (if-let [route (route-map/match [meth uri] routes )]
    (do (log/info "Matched route" (:match route))
        (u/*apply (:match route) (u/deep-merge arg {:request {:route-params (:params route)}})))
    {:response {:status 404 :body {:message (str meth " " uri " not found in")}}}))

(defmethod u/*fn
  ::routes
  [{{routes :routes} :cache}]
  {:response {:body routes}})
