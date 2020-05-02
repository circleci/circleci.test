(require '[circleci.test.report :refer (clojure-test-reporter)])
(require '[circleci.test.report.junit :as junit])
(require '[clojure.java.io :as io])

(def ^:dynamic *global-counter* 0)

{:selectors {:all (constantly true)
             :default (complement :failing)
             :select-vars (fn [m]
                            (.endsWith (str (:name m)) "-2"))
             :combination :combination}
 :test-results-dir (or (System/getenv "CIRCLE_TEST_REPORTS")
                       "test-results")
 :reporters [clojure-test-reporter junit/reporter]
 :global-fixture (fn [f]
                   (try
                     (io/delete-file "global_fixture_test.out" true)
                     (assert (zero? *global-counter*))
                     (binding [*global-counter* (inc *global-counter*)]
                       (spit "global_fixture_test.out" *global-counter* :append true)
                       (f))
                     (finally
                       (io/delete-file "global_fixture_test.out" true)
                       (assert (false? (.exists (clojure.java.io/file "global_fixture_test.out")))))))}
