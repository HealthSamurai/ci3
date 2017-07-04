(ns ci3.repo.bitbucket-test
  (:require [ci3.repo.bitbucket :as sut]
            [unifn.core :as u]
            [ci3.env :as env]
            [matcho.core :as matcho]
            [clojure.test :refer :all]
            [clojure.string :as str]))


(str/trim (env/env :BITBUCKET_KEY)) 
(str/trim (env/env :BITBUCKET_SECRET))

(deftest bitbucket-test
  (matcho/match
   (u/*apply :bitbucket/access-token-request
             {:repository  {:oauthConsumer {:key "wrong" :secret "wrong"}}})
   {::u/status :error
    ::u/errors {:status 400}})

  (matcho/match
   (u/*apply :bitbucket/access-token-request
             {:repository  {:oauthConsumer {:key (env/env :BITBUCKET_KEY)
                                            :secret (env/env :BITBUCKET_SECRET)}}})
   {:bitbucket/access-token-response {:body {:access_token string?}
                                      :status 200}})

  (matcho/match
   (u/*apply :bitbucket/access-token-request
             {:fx/registry {:http identity}
              :repository  {:oauthConsumer {:key "somekey" :secret "somesecret"}}})
   {:bitbucket/access-token-response {:basic-auth "somekey:somesecret"}})


  )
