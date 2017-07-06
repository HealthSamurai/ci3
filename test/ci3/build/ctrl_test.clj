(ns ci3.build.ctrl-test
  (:require [ci3.build.ctrl :as sut]
            [clojure.test :refer :all]
            [unifn.core :as u]))


(deftest build-ctrl-test
  (u/*apply :ci3/on-build
            {:build {:id "b1"}
             :env {:ci3-image "healthsamurai/ci3:spanshot-4"}
             :fx/registry {:k8s/create identity}})
  )

