(ns ci3.server.core
  (:require
   [ci3.server.webhook :as webhook]
   [unifn.rest :as rest]
   [ci3.server.watch]
   [ci3.server.api]
   [ci3.repo.core :as repo]
   [unifn.env]
   [unifn.formats]
   [unifn.core :as u]))

(defmethod u/*fn ::routes
  [{{routes :routes} :cache}]
  {:response {:body (var-get routes)}})

(def routes
  {:GET ::routes
   "repositories" {:GET ::repositories}
   "watches" {:GET :ci3.rest/watches}
   "webhook" { [:id] {:POST ::webhook/webhook
                      :GET  ::webhook/webhook-verify}}
   "builds" {:GET ::builds
             [:id] {:GET ::logs} }})


(def metadata {:cache {:routes #'routes}
               :watch {:timeout 5000
                       :resources [{:handler ::repo/repository
                                    :apiVersion "ci3.io/v1"
                                    :resource :repositories
                                    :ns "default"}
                                   {:handler :ci3.watch/build
                                    :apiVersion "ci3.io/v1"
                                    :resource :builds
                                    :ns "default"}]}
               :web [:unifn.routing/dispatch
                     :unifn.formats/response]
               :bootstrap [:unifn.env/env]
               :config {:web {:port 8888}}})


(defn exec [& args]
  (rest/restart metadata)
  (u/*apply [:unifn.env/env :k8s/watch] metadata))

(comment
  (rest/restart metadata))
