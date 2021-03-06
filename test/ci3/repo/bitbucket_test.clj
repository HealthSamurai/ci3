(ns ci3.repo.bitbucket-test
  (:require [ci3.repo.bitbucket :as sut]
            [unifn.core :as u]
            [matcho.core :as matcho]
            [environ.core :as env]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.string :as str]
            [cheshire.core :as json]))

(deftest parse-payload
  (testing "Parse BB payload"
    (is (= "master" (sut/get-branch (json/parse-string (slurp (io/resource "bb_hook.json")) keyword))))
    )
  )

#_(deftest bitbucket-mock-test
  (matcho/match
   (u/*apply :bitbucket/slug
             {:repository  {:url "https://bitbucket.org/healthsamurai/ci3"}})
   {:repository {:slug "healthsamurai/ci3"}})

  (matcho/match
   (u/*apply :bitbucket/access-token-request
             {:fx/registry {:http identity}
              :repository  {:oauthConsumer {:key "somekey" :secret "somesecret"}}})
   {:tokens {:basic-auth "somekey:somesecret"}})

)

#_(deftest bitbucket-test

  (matcho/match
   (u/*apply :bitbucket/access-token-request
             {:repository  {:oauthConsumer {:key "wrong" :secret "wrong"}}})
   {::u/status :error
    ::u/errors {:status 400}})

  (def auth-req (u/*apply :bitbucket/access-token-request
                          {:repository  {:oauthConsumer {:key (env/env :BITBUCKET_KEY)
                                                         :secret (env/env :BITBUCKET_SECRET)}}}))
  (matcho/match
   auth-req
   {:tokens {:access_token string?}})


  (matcho/match
   (:hooks (u/*apply [:bitbucket/slug
                      :bitbucket/get-hooks]
                     (u/deep-merge auth-req
                                   {:repository  {:url "https://bitbucket.org/healthsamurai/ci3"
                                                  :oauthConsumer {:key (env/env :BITBUCKET_KEY)
                                                                  :secret (env/env :BITBUCKET_SECRET)}}})))


   )


  )

 
