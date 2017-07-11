(ns ci3.server.webhook
  (:require [unifn.core :as u]))


(defmethod u/*fn
  ::webhook
  [{req :request}]
  {:response {:status 200 :body "Ok! Do build"}})

(defmethod u/*fn
  ::webhook-verify
  [{req :request}]
  {:response {:status 200 :body "Ok! Wait for hook"}})
