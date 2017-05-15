(require '[circleci.test.report :refer (clojure-test-reporter)])
(require '[circleci.test.report.junit :as junit])

{:selectors {:all (constantly true)
             :default (complement :failing)}
 :reporters [(clojure-test-reporter)
             (junit/reporter (or (System/getenv "CIRCLE_TEST_REPORTS")
                                 "."))]}
