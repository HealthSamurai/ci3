(defproject ci3 "0.0.1-SNAPSHOT"
  :description "minimalistic ci for k8s"
  :url "http://ci3.org"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha16"]
                 [ch.qos.logback/logback-classic "1.2.2"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/core.async "0.3.443"]
                 [cheshire "5.7.1"]
                 [clj-json-patch "0.1.4"]
                 [hiccup "1.0.5"]
                 [route-map "0.0.4"]
                 [morse   "0.2.4"]
                 [pandect "0.6.1"]
                 [ring/ring-defaults "0.3.0"]
                 [http.async.client "1.2.0"]
                 [http-kit "2.2.0"]
                 [clj-yaml "0.4.0"]
                 [clj-jwt "0.1.1"]
                 [clj-time "0.13.0"]
                 [clojure-humanize "0.2.2"]
                 [environ "1.1.0"]
                 [hiccup "1.0.5"]
                 [io.minio/minio "4.0.0"]
                 [garden "1.3.2"]
                 [matcho "0.1.0-RC6"]]
  :uberjar-name "ci3.jar"
  :resource-paths ["resources"]
  :main ci3.core
  :profiles {:uberjar {:aot :all :omit-source true}})
