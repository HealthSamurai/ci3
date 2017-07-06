(ns unifn.rest
  (:require
   [clojure.string :as str]
   [org.httpkit.server :as http-kit]
   [ring.util.codec]
   [clojure.pprint :as pprint]
   [unifn.routing]
   [clojure.tools.logging :as log]
   [clojure.walk]
   [unifn.core :as u]))

(defn form-decode [s] (clojure.walk/keywordize-keys (ring.util.codec/form-decode s)))

(defn prepare-request [{qs :query-string body :body ct :content-type :as req}]
  (let [req (if body (update req :body slurp) req)
        body (:body req) ]
    (let [params (when qs (form-decode qs))
          form-params (when (and body (= ct "application/x-www-form-urlencoded")) (form-decode body))
          base-url  (str (name (or (:scheme req) "https")) "://" (:server-name req) (when (not (= 80 (:server-port req))) (str ":" (:server-port req))))]
      (cond-> (assoc req :server/base-url base-url)
        params (assoc-in [:params] params)
        form-params (assoc-in [:form-params] form-params)))))

(defn handle [req {stack :web :as arg}]
  (assert stack "Provide :web key in metatada")
  (let [req (prepare-request req)
        {resp :response :as result} (u/*apply stack (assoc arg :request req))]
    (cond
      resp resp
      (::u/status result)  {:body {:message (::u/message result)} :status 500}
      :else {:body (str  "No response after pipeline!" (with-out-str (pprint/pprint (dissoc result :metadata)))) :status 500})))

(defonce server (atom nil))

(defn restart [metadata]
  ;; todo validate config
  (when-let [s @server]
    (log/info "Stoping server")
    (@server)
    (reset! server nil))
  (let [metadata (if-let [bootstrap (:bootstrap metadata)]
                   (u/*apply bootstrap metadata)
                   metadata)
        port (or (get-in metadata [:config :web :port]) 8080)]
    (log/info "Starting server on " port)
    (reset! server (http-kit/run-server #(handle % metadata) {:port port}))
    metadata))

(defn test-server [metadata]
  (let [metadata (if-let [bootstrap (:bootstrap metadata)]
                   (u/*apply bootstrap metadata)
                   metadata)]
    (fn [arg]
      (let [{stack :web} metadata]
        (u/*apply stack (merge metadata arg))))))


(comment
  (println @server)
  )

