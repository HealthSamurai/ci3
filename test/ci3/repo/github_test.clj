(ns ci3.repo.github-test
  (:require [ci3.repo.github :as sut]
            [clojure.test :refer :all]))

(deftest parse-payload
  (testing "Parse github payload"
    (is (= "master" (sut/get-branch {:ref "refs/heads/master"})))
    (is (= "dev" (sut/get-branch {:ref "refs/heads/dev"})))
    (is (= "production" (sut/get-branch {:ref "refs/heads/production"})))
    )
  )
