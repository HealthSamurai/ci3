(ns ci3.repo.core-test
  (:require [ci3.repo.core :as sut]
            [matcho.core :refer [match]]
            [unifn.core :as u]
            [clojure.test :refer :all]))

(deftest repo-core
  (match
   (u/*apply
    ::sut/webhook
    {:request {:route-params {:id "some-fake-id"}}})
   {:response {:status 400}})

  (match
   (u/*apply
    ::sut/webhook
    {:request {:route-params {:id "ci3"}}})
   {:response {:status 200}})
  )

