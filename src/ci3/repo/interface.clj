(ns ci3.repo.interface
  (:require [clojure.tools.logging :as log]
            [unifn.core :as u]))

(defmulti init
  (fn [_ repo]
    (keyword (or (:type repo) "github"))))

(defmethod init
  :default
  [_ repo]
  (log/info "No handler for repo " repo))

(defn repo-type
  [{repository :ci3.repo.core/repository}]
  (or (keyword (:type repository)) :github))

(defmulti mk-build repo-type)
(defmulti checkout-project repo-type)

(comment
  (ns-unmap *ns* 'checkout-project)
  (ns-unmap *ns* 'repo-type)
  (ns-unmap *ns* 'mk-build)
  (ns-unmap *ns* 'init))
