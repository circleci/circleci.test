(defproject circleci/circleci.test "0.1.0"
  :description "Clojure.test compatible test-runner"
  :url "https://github.com/circleci/circleci.test"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :pedantic? :abort
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:uberjar {:aot :all}})
