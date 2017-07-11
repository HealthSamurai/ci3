(ns unifn.formats
  (:require [org.httpkit.server :as http-kit]
            [ring.util.codec]
            [cheshire.core :as json]
            [cheshire.generate :as json-gen]
            [clj-yaml.core :as yaml]
            [clojure.pprint :as pprint]
            [ring.util.io]
            [clj-time.format :as tfmt]
            [unifn.core :as u]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import org.httpkit.server.AsyncChannel
           [java.io BufferedWriter OutputStreamWriter  ByteArrayInputStream ByteArrayOutputStream]))

(json-gen/add-encoder org.httpkit.server.AsyncChannel json-gen/encode-str)
(json-gen/add-encoder clojure.lang.Var json-gen/encode-str)

(def date-to-json-formatter (tfmt/formatters :date-time))
(json-gen/add-encoder
 org.joda.time.DateTime
 (fn  [d json-generator]
   (.writeString json-generator (tfmt/unparse date-to-json-formatter d))))

(defn generate-stream
  ([data] (generate-stream data nil))
  ([data options]

   (ring.util.io/piped-input-stream
    (fn [out] (json/generate-stream
               data (-> out (OutputStreamWriter.) (BufferedWriter.)) options)))))


(defmulti do-format (fn [fmt _] fmt))

(defmethod do-format :json [_ body]
  (generate-stream body))

(defmethod do-format :yaml [_ body]
  (yaml/generate-string body))

(defmethod do-format :edn [_ body]
  (with-out-str (pprint/pprint body)))


(defmulti parse-format (fn [fmt _] fmt))

(defmethod parse-format :json [_ b]
  (when b
    (cond
      (string? b) (json/parse-string b keyword)
      (instance? java.io.InputStream b) (json/parse-stream (io/reader b) keyword)
      :else b)))

(defmethod parse-format :yaml [_ b]
  (when b
    (cond
      (string? b) (yaml/parse-string b true)
      (instance? java.io.InputStream b) (throw (Exception. "Ups fixme"))
      :else b)))

(defn form-decode [s] (clojure.walk/keywordize-keys (ring.util.codec/form-decode s)))
(defmethod parse-format :query-string [_ b]
  (when b
    (cond
      (string? b) (form-decode b)
      (instance? java.io.InputStream b) (throw (Exception. "Ups fixme"))
      :else b)))

(def ct-mappings
  {"application/json" :json
   "application/json; charset=utf8" :json
   "application/json; charset=utf-8" :json
   "application/transit+json" :transit
   "text/yaml" :yaml
   "text/edn" :edn
   "*/*" :json
   "application/x-www-form-urlencoded" :query-string
   "application/yaml" :yaml
   "application/edn" :edn})

(defn header-to-format [ct]
  (or (get ct-mappings ct) ct))

(defn content-type [fmt]
  (get {:edn "text/edn"
        :json "application/json"
        :yaml "text/yaml"} fmt))

(defn parse-accept-header [ct]
  (str/split ct #"[,;]"))

(defn accept-header-to-format [ct]
  (when (and ct (string? ct))
    (some ct-mappings (parse-accept-header ct))))

(def supported-formats #{:json :yaml :edn})

(defmethod u/*fn
  ::response
  [{{body :body {ct "content-type"} :headers} :response
    {{fmt :_format} :params {ac "accept"} :headers} :request
    df :default-format
    :as arg}]
  (when-not ct
    (let [fmt (cond
                fmt (keyword fmt)
                ac (accept-header-to-format ac)
                :else (keyword (or df :json)))]
      (if (contains? supported-formats fmt)
        (when (and body (or (vector? body) (map? body)))
          {:response {:body    (do-format fmt body)
                      :headers {"content-type" (content-type fmt)}}})
        {:response {:body (str "Unknown format '" (when fmt (name fmt)) "'")
                    :status 422}}))))

(defmethod u/*fn
  ::body
  [{{body :body {ct "content-type"} :headers {fmt :_format} :params} :request :as arg}]
  (let [fmt (keyword (or fmt (header-to-format ct) "json"))]
    (when body {:request{:resource (parse-format fmt body)}})))
