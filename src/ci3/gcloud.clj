(ns ci3.gcloud
  (:require [cheshire.core :as json]
            [clj-jwt.core :as jwt]
            [clj-jwt.key :as key]
            [clj-time.core :as time]
            [org.httpkit.client :as http-client]
            [clojure.string :as str])
  (:import java.io.StringReader))


;; ;;; Example code for calling Google apis using a service account.

(defn load-creds
  "Takes a path to a service account .json credentials file"
  []
  (when-let [sk (or (System/getenv "DOCKER_KEY") ;; replce with service account
                    (System/getenv "SERVICE_ACCOUNT"))]
    (-> sk (json/parse-string keyword))))

(keys (load-creds))


;; list of API scopes requested, e.g. https://developers.google.com/admin-sdk/directory/v1/guides/authorizing
(def scopes ["https://www.googleapis.com/auth/devstorage.full_control"
             "https://www.googleapis.com/auth/devstorage.full_control"])

(defn create-claim [creds & [{:keys [sub] :as opts}]]
  (let [claim (merge {:iss (:client_email creds)
                      :scope (str/join " " scopes)
                      :aud "https://www.googleapis.com/oauth2/v4/token"
                      :exp (-> 1 time/hours time/from-now)
                      :iat (time/now)}
                     (when sub
                       ;; when using the Admin API, delegating access, :sub may be needed
                       {:sub sub}))]
    (-> claim jwt/jwt (jwt/sign :RS256 (-> creds :private_key (#(StringReader. %)) (#(key/pem->private-key % nil)))) (jwt/to-str))))

(defn request-token [creds & [{:keys [sub] :as opts}]]
  (let [claim (create-claim creds opts)
        resp @(http-client/post "https://www.googleapis.com/oauth2/v4/token"
                                {:form-params {:grant_type "urn:ietf:params:oauth:grant-type:jwt-bearer"
                                               :assertion claim}})]
    resp
    (when (= 200 (-> resp :status))
      (-> resp :body (json/parse-string keyword) :access_token))))

(defn get-access-token []
  (request-token (load-creds) {}))

(comment
  (request-token (load-creds) {})
  )


;; ;; Call request-token to make an API call to google to create a new access token, using creds. Access-tokens are valid for 1 hour after creating. Pass the received token to API calls.
