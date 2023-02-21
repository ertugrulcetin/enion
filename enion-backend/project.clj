(defproject enion-backend "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[ch.qos.logback/logback-classic "1.4.4"]
                 [clojure.java-time "1.1.0"]
                 [cprop "0.1.19"]
                 [luminus-aleph "0.2.0"]
                 [aleph "0.6.0"]
                 [luminus-transit "0.1.5"]
                 [luminus/ring-ttl-session "0.3.3"]
                 [metosin/muuntaja "0.6.8"]
                 [metosin/reitit "0.5.18"]
                 [metosin/ring-http-response "0.9.3"]
                 [mount "0.1.16"]
                 [nrepl "1.0.0"]
                 [org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.cli "1.0.214"]
                 [org.clojure/tools.logging "1.2.4"]
                 [ring/ring-core "1.9.6"]
                 [ring/ring-defaults "0.3.4"]
                 [selmer "1.12.55"]
                 [clojure-msgpack "1.2.1"]
                 [amalloy/ring-gzip-middleware "0.1.4"]
                 [ring-cors/ring-cors "0.1.13"]
                 [weavejester/dependency "0.2.1"]]

  :min-lein-version "2.0.0"

  :source-paths ["src/clj" "../common/src"]
  :test-paths ["test/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main ^:skip-aot enion-backend.core

  :plugins []

  :profiles
  {:uberjar {:omit-source true
             :aot [enion-backend.core]
             :uberjar-name "enion-backend.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:jvm-opts ["-Dconf=dev-config.edn"]
                  :dependencies [[org.clojure/tools.namespace "1.3.0"]
                                 [pjstadig/humane-test-output "0.11.0"]
                                 [prone "2021-04-23"]
                                 [ring/ring-devel "1.9.6"]
                                 [ring/ring-mock "0.4.0"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.24.1"]
                                 [jonase/eastwood "1.2.4"]
                                 [cider/cider-nrepl "0.26.0"]]

                  :source-paths ["env/dev/clj"]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user
                                 :init (start)
                                 :timeout 120000}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:jvm-opts ["-Dconf=test-config.edn"]
                  :resource-paths ["env/test/resources"]}
   :profiles/dev {}
   :profiles/test {}})
