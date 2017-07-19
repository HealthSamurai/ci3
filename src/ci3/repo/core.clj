(ns ci3.repo.core
  (:require [cheshire.core :as json]
            [ci3.repo.interface :as interf]
            [ci3.repo.bitbucket :as bitbucket]
            [ci3.repo.github :as github]
            [ci3.k8s :as k8s]
            [unifn.core :as u]))

(defmethod u/*fn
  ::load-repo
  [{cfg :k8s :as arg}]
  (let [repo-id (get-in arg [:request :route-params :id])
        repo (k8s/find cfg :repositories repo-id)]
    (println repo)
    (if (or (nil? repo)
            (= "Failure" (:status repo)))
      {::u/status :error
       ::u/message "Hook not found"}
      {::repository repo})))

(defmethod u/*fn
  ::catch-errors
  [{error ::u/message}]
  (when error
    {:response {:status 400
                :body error}}))
(defmethod u/*fn
  ::response
  [{build ::build}]
  (when build
    {:response {:status 200
                :body (json/generate-string build)}}))

(defmethod u/*fn
  ::mk-build [arg]
  (interf/mk-build arg))

(defmethod u/*fn
  ::mk-build-name
  [{repo ::repository}]
  {::build-name (str (get-in repo [:metadata :name]) "-"
                     (System/currentTimeMillis))})

(defmethod u/*fn
  ::create-build
  [{build ::build cfg :k8s}]
  {:ci3.repo.core/build
   (clojure.walk/keywordize-keys (k8s/create cfg :builds build))})

(defmethod u/*fn
  ::webhook
  [arg]
  (u/*apply
   [::load-repo
    ::mk-build-name
    ::mk-build
    ::create-build
    ::response
    ::catch-errors {::u/intercept :all}]
   arg))

(comment
  (->
   (u/*apply
    ::webhook
    {:request {:route-params {:id "some-fake-id"}}})
   :response))
