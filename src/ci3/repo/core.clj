(ns ci3.repo.core
  (:require [cheshire.core :as json]
            [ci3.repo.bitbucket :as bitbucket]
            [ci3.repo.github :as github]
            [ci3.repo.interface :as interf]
            [ci3.k8s :as k8s]
            [unifn.core :as u]))

(defmethod u/*fn
  ::load-repo
  [arg]
  (let [repo-id (get-in arg [:request :route-params :id])
        repo (k8s/find k8s/cfg :repositories repo-id)]
    (if (= "Failure" (:status repo))
      {::error "Hook not found"}
      {::repository repo})))

(defmethod u/*fn
  ::catch-errors
  [{error ::error}]
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
  ::mk-build
  [arg]
  {::build (interf/mk-build arg)})

(defmethod u/*fn
  ::webhook
  [arg]
  (u/*apply
   [::load-repo
    ::mk-build
    ::response
    ::catch-errors]
   arg))

(comment
  (->
   (u/*apply
    ::webhook
    {:request {:route-params {:id "some-fake-id"}}})
   :response))
