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

(defmulti mk-build
  (fn [{repository :ci3.repo.core/repository :as arg}]
    (println repository)
    (or (keyword (:type repository)) :github)))

(comment
  (ns-unmap *ns* 'mk-build)
  (ns-unmap *ns* 'init))
