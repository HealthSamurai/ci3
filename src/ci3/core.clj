(ns ci3.core
  (:require [ci3.server :as server]
            [ci3.agent :as agent]
            [ci3.local :as local])
  (:gen-class))

(defn -main [& args]
  (let [cmd (first args)]
    (println cmd args)))
