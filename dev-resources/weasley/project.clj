(load-file "./circleci.test.version")
(defproject circleci/weasley "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [circleci/circleci.test ~circleci-test-version]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :main weasley.core
  :aot [weasley.core]
  :aliases {"test" ["run" "-m" "circleci.test/dir" :project/test-paths]
            "tests" ["run" "-m" "circleci.test"]
            "retest" ["run" "-m" "circleci.test.retest"]})
