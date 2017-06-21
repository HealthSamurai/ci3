(defproject ci3 "0.0.1-SNAPSHOT"
  :description "minimalistic ci for k8s"
  :url "http://ci3.org"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha15"]
                 ;; [ch.qos.logback/logback-classic "1.2.2"]
                 [cheshire "5.7.1"]
                 [clj-json-patch "0.1.4"]
                 [org.clojure/core.async "0.3.443"]
                 [hiccup "1.0.5"]
                 [http-kit "2.2.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [pandect "0.6.1"]
                 [ring/ring-defaults "0.3.0"]
                 [route-map "0.0.4"]
                 [clj-yaml "0.4.0"]
                 [morse   "0.2.4"]
                 [environ   "1.1.0" ]
                 [clj-jwt "0.1.1"]
                 [clj-time "0.13.0"]
                 [clojure-humanize "0.2.2"]
                 [hiccup "1.0.5"]
                 [garden "1.3.2"]
                 ]
  :uberjar-name "ci3.jar"
  :main ci3.core
  :profiles {:uberjar {:aot :all :omit-source true}})
