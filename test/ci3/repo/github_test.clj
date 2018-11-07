(ns ci3.repo.github-test
  (:require [ci3.repo.github :as sut]
            [clojure.test :refer :all]))

(deftest parse-payload
  (testing "Parse github payload"
    (is (= "master" (sut/get-branch {:ref "refs/heads/master"})))
    (is (= "dev" (sut/get-branch {:ref "refs/heads/dev"})))
    (is (= "production" (sut/get-branch {:ref "refs/heads/production"})))

    (is (= "dev" (sut/get-branch {:ref "foo/tags/0.2.1"
                                  :base_ref "refs/heads/dev"})))

    (is (= "UNKNOWN-BRANCH" (sut/get-branch {:ref "foo/tags/0.2.1"
                                             :base_ref nil}))))

  (testing "Parse tags"
    (is (= "0.2.1" (sut/get-tag {:ref "foo/tags/0.2.1"
                                 :base_ref "refs/heads/dev"})))
    (is (= nil (sut/get-tag {:ref "foo/tags-/0.2.1"
                             :base_ref "refs/heads/dev"})))
    )
  )
