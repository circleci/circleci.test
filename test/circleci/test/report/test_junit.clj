(ns circleci.test.report.test-junit
  (:require [clojure.test :refer (deftest testing is)]
            circleci.test
            circleci.test.report
            [circleci.test.report.junit :as junit])
  (:import clojure.data.xml.Element
           java.nio.file.Files
           java.nio.file.attribute.FileAttribute))

(deftest failure-works
  (is (instance? Element (#'junit/failure-xml
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
    (is (instance? Element ret))
    (testing "it has a time"
      (is (-> ret :attrs :time float?)))))

(deftest testsuite-works
  (is (instance? Element
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
    (binding [circleci.test.report/*reporters* [(junit/reporter (str tempdir))]]
      (is (map? (circleci.test/run-tests 'circleci.test-config))))))
