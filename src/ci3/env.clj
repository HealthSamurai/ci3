(ns ci3.env
  (:require [clojure.string :as str]))

(defn env [k]
  (str/trim (System/getenv (name k))))

(defn environment []
  (let [hostname (env :HOSTNAME)
        schema (or (env :SCHEMA) "http")]
    {:hostname hostname 
     :hooks-url (str schema "://" hostname "/webhook")}))

(comment

  (environment)

  )


