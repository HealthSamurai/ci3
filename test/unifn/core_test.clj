(ns unifn.core-test
  (:require [unifn.core :as u]
            [matcho.core :as matcho]
            [clojure.test :refer :all]
            [clojure.spec.alpha :as s]))

(defmethod u/*fn :test/transform
  [arg]
  {:var "value"})

(defmethod u/*fn :test/response
  [arg]
  {:response  {:some "response"}})

(defmethod u/*fn :test/interceptor
  [arg]
  (when (:intercept arg)
    {:response {:interecepted true} ::u/status :stop}))

(defmethod u/*fn :test/catch
  [arg]
  {:catched true})

(defmethod u/*fn :test/throwing
  [arg]
  (throw (Exception. "ups")))

(s/def :test/specified
  (s/keys :req [:test/specific-key]))

(defmethod u/*fn :test/specified
  [arg]
  {:specified true})

(deftest unifn-basic-test

  (matcho/match
   (u/*apply identity {:a 1})
   {:a 1})

  (matcho/match
   (u/*apply {::u/fn :test/transform} {:some "payload"})
   {::u/fn #(= "transform" (name %))
    :var "value"
    :some "payload"})

  (matcho/match
   (u/*apply {::u/fn :test/unexisting} {:some "payload"})
   {::u/fn #(= "unexisting" (name %))
    ::u/status :error})

  (is (thrown? Exception (u/*apply {::u/fn :test/throwing} {:some "payload"})))

  (matcho/match
   (u/*apply {::u/fn :test/throwing} {:some "payload" ::u/safe? true})
   {::u/status :error
    ::u/message string?})


  (matcho/match
   (u/*apply [{::u/fn :test/transform}
              {::u/fn :test/interceptor}
              {::u/fn :test/response}]
             {:request {}})

   {:response {:some "response"} :var "value" })

  (matcho/match
   (u/*apply [{::u/fn :test/transform}
              {::u/fn :test/interceptor}
              {::u/fn :test/response}]
             {:request {} :intercept true})
   {:response {:interecepted true}})

  (matcho/match
   (u/*apply [{::u/fn :test/interceptor}
              {::u/fn :test/response}
              {::u/fn :test/catch ::u/intercept :all}
              {::u/fn :test/response}]
             {:intercept true})
   {:response {:interecepted true}
    :catched true})

  (matcho/match
   (u/*apply {::u/fn :test/specified} {})
   {::u/status :error
    ::u/message string?})

  (matcho/match
   (u/*apply {::u/fn :test/specified} {:test/specific-key 1})
   {:specified true}))


(defmethod u/*fn :test/http
  [{uri :uri}]
  (println  (str "result of "  uri))
  {:body (str "result of "  uri)})

(defmethod u/*fn :test/effects
  [arg]
  {:fx {:test/http {:uri (:uri arg)
                    :fx/result [:myresult]}}})


(deftest test-effects
  (matcho/match
   (u/*apply :test/effects {:uri "http://google.com"})

   {:fx nil
    :myresult {:body "result of http://google.com"}})

  ;; override registry
  (matcho/match
   (u/*apply :test/effects {:uri "http://google.com"
                            :fx/registry {:test/http (fn [x] {:body "ups"})}})

   {:fx nil
    :myresult {:body "ups"}})

  )
