(ns ci3.server.webhook
  (:require [unifn.core :as u]
            [ci3.repo.interface :as interf]))

(defmethod u/*fn
  ::webhook
  [{req :request :as arg}]
  (interf/webhook arg))

(defmethod u/*fn
  ::webhook-verify
  [{req :request}]
  {:response {:status 200 :body "Ok! Wait for hook"}})
