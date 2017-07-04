(ns ci3.repo.interface
  (:require [clojure.tools.logging :as log]))


(defmulti init (fn [_ repo] (keyword (or (get-in repo ["type"]) "github"))))
(defmethod init
  :default
  [_ repo]
  (log/info "No handler for repo " repo))
