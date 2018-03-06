(ns circleci.test.report.test-junit
  (:require [clojure.test :refer (deftest testing is)]
            [clojure.data.xml.node :refer [element?]]
            circleci.test
            circleci.test.report
            [circleci.test.report.junit :as junit])
  (:import java.nio.file.Files
           java.nio.file.attribute.FileAttribute))

(deftest failure-works
  (is (element? (#'junit/failure-xml
                 {:expected (= 3 (+ 1 1)),
                  :message nil,
                  :type :fail,
                  :actual (not (= 3 2)),
                  :file "test_junit.clj",
                  :line 8}))))

(deftest testcase-works
  (let [ret (#'junit/testcase-xml {:type :end-test-var
                                   :name #'clojure.core/map
                                   :elapsed 0.03}
                                  (#'junit/failure-xml
                                   {:expected (= 3 (+ 1 1)),
                                    :message nil,
                                    :type :fail,
                                    :actual (not (= 3 2)),
                                    :file "test_junit.clj",
                                    :line 8}))]
    (is (element? ret))
    (testing "it has a time"
      (is (-> ret :attrs :time float?)))))

(deftest testsuite-works
  (is (element?
       (#'junit/suite-xml {:ns (find-ns 'clojure.core)}
                          [(#'junit/testcase-xml {:type :end-test-var
                                                  :name #'clojure.core/map
                                                  :elapsed 0.03}
                                                 [(#'junit/failure-xml
                                                   {:expected (= 3 (+ 1 1)),
                                                    :message nil,
                                                    :type :fail,
                                                    :actual (not (= 3 2)),
                                                    :file "test_junit.clj",
                                                    :line 8})])]))))

(deftest junit-doesn't-break-return-code
  ;; `lein test` assumes tests return a map.
  (let [tempdir (Files/createTempDirectory "circleci.test.report.test-junit" (into-array FileAttribute []))]
    (-> tempdir .toFile .deleteOnExit)
    (binding [circleci.test.report/*reporters* [(junit/reporter {:test-results-dir (str tempdir)})]]
      (is (map? (circleci.test/run-tests 'circleci.test-config))))))
