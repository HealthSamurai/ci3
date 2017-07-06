(ns ci3.server.api
  (:require [ci3.server.watch :as watch]
            [unifn.core :as u]))


(defmethod u/*fn
  :ci3.rest/watches
  [_]
  {:response {:status 200
              :body (with-out-str (clojure.pprint/pprint  @watch/state))}})
