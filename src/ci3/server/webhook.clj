(ns ci3.server.webhook
  (:require [unifn.core :as u]))


(defmethod u/*fn
  :ci3/webhook
  [{req :request}]
  {:response {:status 200 :body "Ok!"}})
