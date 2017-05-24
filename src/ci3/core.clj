(ns ci3.core
  (:require [ci3.server :as server]
            [ci3.agent :as agent]
            [ci3.local :as local])
  (:gen-class))

(defn -main [& args]
  (let [cmd (first args)]
    (cond
      (= "agent" cmd) (agent/exec (rest args))
      (= "server" cmd) (server/exec (rest args))
      (= "run" cmd) (local/exec (rest args))
      :else (println "Use one of subcomands: agent, server or run" ))))
