(ns ci3.core
  (:require
   [ci3.agent.core :as agent]
   [ci3.server.core :as server])
  (:gen-class))

(defn -main [& args]
  (let [cmd (first args)]
    (cond
      (= "agent" cmd)  (agent/exec (rest args))
      (= "server" cmd) (server/exec (rest args))
      (= "run" cmd)    (agent/local (rest args))
      :else (println "Use one of subcomands: agent, server or run" ))))
