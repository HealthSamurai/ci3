(ns ci3.server.watch
  (:require [unifn.core :as u]
            [http.async.client :as http]
            [http.async.client.cert :as cert]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [ci3.build.core]
            [ci3.k8s :as k8s]
            [ci3.repo.core]
            [cheshire.core :as json])
  (:import
   com.fasterxml.jackson.core.JsonParseException
   (javax.net.ssl
    KeyManagerFactory
    SSLContext
    HttpsURLConnection
    TrustManager
    X509TrustManager)))

(defonce state (atom {}))

;; zero ssl context


(defrecord BlindTrustManager
    []
  X509TrustManager
  (checkClientTrusted [this chain auth-type] nil)
  (checkServerTrusted [this chain auth-type] nil)
  (getAcceptedIssuers [this] nil))

(defn #^SSLContext ssl-context
  []
  (let [ctx (SSLContext/getInstance "TLS")
        trust-managers (into-array javax.net.ssl.X509TrustManager
                                   (list (new BlindTrustManager)))]
    (.init ctx nil trust-managers nil)
    ctx))



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


(defn set-request-data [rt k v]
  (assoc-in @state [:requests rt k] v))

(defn get-request-data [rt k]
  (get-in @state [:requests rt k]))

(defn *parse-json-stream [acc s]
  (if (str/includes? s "\n")
    (let [[json-str rest-msg] (str/split s #"\n" 2)]
      (try (*parse-json-stream (conj acc (json/parse-string json-str keyword)) rest-msg)
           (catch com.fasterxml.jackson.core.JsonParseException e
             (println "JSON ERROR WHILE PARSE" s)
             [s acc])))
    [s acc]))

(*parse-json-stream [] "{\"q\":2}\n{\"q\":1}\n")

(defn parse-json-stream [rt st]
  (let [[rest-of-msg resources]
        (*parse-json-stream  [] (str (or (get-request-data rt :message) "") (-> st .toByteArray String.)))]

    (set-request-data rt :message rest-of-msg)
    resources))

(defn mk-on-change [env opts retry]
  (fn [st body]
    (doseq [res (parse-json-stream (:resource opts) body)]
      (update-version (:resource opts) (get-in res [:object :metadata :resourceVersion]))
      ;; sometimes version is compacted :(
      (if (and (= "ERROR" (:type res)) (= "Expired" (get-in res [:object :reason])))
        (do (println "OUTDATE version:" res)
            (reset-version (:resource opts))
            (retry))
        (do (println opts " for v:" (get-in res [:object :metadata :resourceVersion]))
            (println "->" (:handler opts))
            (u/*apply (:handler opts) {:env env :resource (k8s/resolve-secrets res)}))))
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
        on-change (mk-on-change env opts #(do-watch env client opts))]
    (println "Watch " opts " from " v)
    (->> (http/request-stream client :get (:url q) on-change
                              :headers {"Authorization" (str "Bearer " (:kube-token env))}
                              :insecure? true
                              :timeout 300000
                              :query (:query q))
         (assoc opts :request)
         (swap! state assoc-in [:requests (:resource opts)]))))

(defn start [{env :env cfg :watch}]
  (println "Start watching resources")
  (if-let [c (:client @state)]
    (.close c)
    (swap! state dissoc :client :requests))

  (let [client (http/create-client :ssl-context (ssl-context))]
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
        (Thread/sleep 10000)))
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
            :resources [{:handler :ci3.watch/repository
                         :apiVersion "ci3.io/v1"
                         :resource :repositories
                         :ns "default"}
                        {:handler :ci3.watch/build
                         :apiVersion "ci3.io/v1"
                         :resource :builds
                         :ns "default"}]}}))


