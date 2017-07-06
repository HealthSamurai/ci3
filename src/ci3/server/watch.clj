(ns ci3.server.watch
  (:require [unifn.core :as u]
            [http.async.client :as http]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [ci3.build.core]
            [ci3.repo.core]
            [cheshire.core :as json]))

(defonce state (atom {}))

(defn parse-json-stream [st]
  (-> st
      .toByteArray
      String.
      (str/split #"\n")
      (->> (mapv #(json/parse-string % keyword))
           reverse)))

(defn update-version [rt v]
  (when v
    (let [v (cond (string? v) (Integer/parseInt v)
                  (number? v) v)]
      (swap! state update-in [:requests rt :version]
             (fn [x] (if x (max x v) v))))))

(defn reset-version [rt]
  (swap! state assoc-in [:requests rt :version] nil))

(defn get-version [rt]
  (get-in @state [:requests rt :version]))

(defn mk-on-change [opts retry]
  (fn [st body]
    (doseq [res (parse-json-stream body)]
      (update-version (:resource opts)
                      (get-in res [:object :metadata :resourceVersion]))
      ;; sometimes version is compacted :(
      (if (and (= "ERROR" (:type res)) (= "Expired" (get-in res [:object :reason])))
        (do (println "OUTDATE version:" res)
            (reset-version (:resource opts))
            (retry))
        (do (println opts " for v:" (get-in res [:object :metadata :resourceVersion]))
            (println "->" (:handler opts))
            (u/*apply (:handler opts) {:resource res}))))
    [body :continue]))

(defn build-watch-query
  [{kube-url :kube-url}
   {res :resource n :ns api :apiVersion}]
  {:url (str kube-url "/apis/" api "/namespaces/" n "/" (name res)) 
   :query  {:watch true}})

(defn do-watch [env client opts]
  (let [v (get-version (:resource opts)) 
        q (build-watch-query env opts)
        q (if v (assoc-in q [:query :resourceVersion] v) q)
        on-change (mk-on-change opts #(do-watch env client opts))]
    (println "Watch " opts " from " v)
    (->> (http/request-stream client :get (:url q) on-change
                              :timeout 300000
                              :query (:query q))
         (assoc opts :request)
         (swap! state assoc-in [:requests (:resource opts)]))))

(defn start [{env :env cfg :watch}]
  (println "Start watching resources")
  (if-let [c (:client @state)]
    (.close c)
    (swap! state dissoc :client :requests))

  (let [client (http/create-client)]
    (swap! state assoc :client client)
    (doseq [res-cfg (:resources cfg)]
      (#'do-watch env client res-cfg))))

(defn supervisor [env]
  (future
    (println "Start supervisor")
    (while (not (:stop @state))
      (let [client (:client @state)]
        (if (.isClosed client)
          (do (println "Client closed, run it")
              (start))
          (doseq [[rt {req :request :as opts}] (:requests @state)]
            (cond (realized? (:error req))
                  (do (println "ERRORED:" opts)
                      (#'do-watch env client (dissoc opts :request)))
                  (realized? (:done req))
                  (do (println "DONE:" opts)
                      (#'do-watch env client (dissoc opts :request))))))
        (Thread/sleep 2000)))
    (println "Stopping supervisor")
    (swap! state :stop false)))

(defmethod u/*fn :k8s/watch
  [{env :env watch :watch :as arg}]
  (start arg)
  (supervisor env)
  {:started true})


(comment
  (:version (:repositories (:requests @state)))
  (swap! state :stop true)
  (swap! state :stop false)
  state

  (supervisor {:kube-url "http://localhost:8001"})
  (start
   {:env {:kube-url "http://localhost:8001"}
    :watch {:timeout 5000
             :resources [{:handler :ci3.repo/ctrl
                          :apiVersion "ci3.io/v1"
                          :resource :repositories
                          :ns "default"}
                         {:handler :ci3.build/ctrl
                          :apiVersion "ci3.io/v1"
                          :resource :builds
                          :ns "default"}]}}))


