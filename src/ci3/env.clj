(ns ci3.env
  (:require [clojure.string :as str]))

(defn env [k]
  (when-let [x (System/getenv (name k))]
    (str/trim x)))

(defn environment []
  (let [hostname (env :HOSTNAME)
        schema (or (env :SCHEMA) "http")]
    {:hostname hostname 
     :hooks-url (str schema "://" hostname "/webhook")}))

(comment

  (environment)

  )


