(ns unifn.env
  (:require
   [unifn.core :as u]
   [clojure.string :as str]))

(defn- normalize-name [k]
  (-> k str/lower-case
      (str/replace "_" "-")
      keyword))

(defmethod u/*fn
  ::env [arg]
  {:env (reduce (fn [acc [k v]]
                  (assoc acc (normalize-name k) v))
                {} (System/getenv))})


(comment
  (u/*apply ::env {})
  )
