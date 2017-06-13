(require '[circleci.test.report :refer (clojure-test-reporter)])
(require '[circleci.test.report.junit :as junit])

(def ^:dynamic *inside-global* false)

{:selectors {:all (constantly true)
             :default (complement :failing)}
 :reporters [(clojure-test-reporter)
             (junit/reporter (or (System/getenv "CIRCLE_TEST_REPORTS")
                                 "test-results"))]
 :global-fixture (fn [f]
                   (assert (not *inside-global*))
                   (binding [*inside-global* true]
                     (f)))}
