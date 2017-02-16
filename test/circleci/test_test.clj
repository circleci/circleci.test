;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

;;; circleci/test-test.clj: unit tests for test.clj

;; by Stuart Sierra
;; January 16, 2009

;; Thanks to Chas Emerick, Allen Rohner, and Stuart Halloway for
;; contributions and suggestions.


(ns circleci.test-test
  (:use circleci.test)
  (:require [clojure.stacktrace :as stack]
            [circleci.test :as t]
            [circleci.test.report :as report]
            [clojure.test :refer (deftest testing is are)]))

(deftest can-test-symbol
  (let [x true]
    (is x "Should pass"))
  (let [x false]
    (is x "Should fail")))

(deftest can-test-boolean
  (is true "Should pass")
  (is false "Should fail"))

(deftest can-test-nil
  (is nil "Should fail"))

(deftest can-test-=
  (is (= 2 (+ 1 1)) "Should pass")
  (is (= 3 (+ 2 2)) "Should fail"))

(deftest can-test-instance
  (is (instance? Long (+ 2 2)) "Should pass")
  (is (instance? Float (+ 1 1)) "Should fail"))

(deftest can-test-thrown
  (is (thrown? ArithmeticException (/ 1 0)) "Should pass")
  ;; No exception is thrown:
  (is (thrown? Exception (+ 1 1)) "Should fail")
  ;; Wrong class of exception is thrown:
  (is (thrown? ArithmeticException (throw (RuntimeException.))) "Should error"))

(deftest can-test-thrown-with-msg
  (is (thrown-with-msg? ArithmeticException #"Divide by zero" (/ 1 0)) "Should pass")
  ;; Wrong message string:
  (is (thrown-with-msg? ArithmeticException #"Something else" (/ 1 0)) "Should fail")
  ;; No exception is thrown:
  (is (thrown? Exception (+ 1 1)) "Should fail")
  ;; Wrong class of exception is thrown:
  (is (thrown-with-msg? IllegalArgumentException #"Divide by zero" (/ 1 0)) "Should error"))

(deftest can-catch-unexpected-exceptions
  (is (= 1 (throw (Exception.))) "Should error"))

(deftest can-test-method-call
  (is (.startsWith "abc" "a") "Should pass")
  (is (.startsWith "abc" "d") "Should fail"))

(deftest can-test-anonymous-fn
  (is (#(.startsWith % "a") "abc") "Should pass")
  (is (#(.startsWith % "d") "abc") "Should fail"))

(deftest can-test-regexps
  (is (re-matches #"^ab.*$" "abbabba") "Should pass")
  (is (re-matches #"^cd.*$" "abbabba") "Should fail")
  (is (re-find #"ab" "abbabba") "Should pass")
  (is (re-find #"cd" "abbabba") "Should fail"))

(deftest clj-1102-empty-stack-trace-should-not-throw-exceptions
  (let [empty-stack (into-array (Class/forName "java.lang.StackTraceElement")
                                [])
        t (doto (Exception.) (.setStackTrace empty-stack))]
    (is (map? (#'clojure.test/stacktrace-file-and-line empty-stack)) "Should pass")
    (is (string? (with-out-str (stack/print-stack-trace t))) "Should pass")))

(deftest #^{:has-meta true} can-add-metadata-to-tests
  (is (:has-meta (meta #'can-add-metadata-to-tests)) "Should pass"))

;; still have to declare the symbol before testing unbound symbols
(declare does-not-exist)

#_(deftest can-test-unbound-symbol
  (is (= nil does-not-exist) "Should error"))

#_(deftest can-test-unbound-function
  (is (does-not-exist) "Should error"))


;; Here, we create an alternate version of test/report, that
;; compares the event with the message, then calls the original
;; 'report' with modified arguments.

(declare ^:dynamic original-report)

(defn custom-report [data]
  (let [event (:type data)
        msg (:message data)
        expected (:expected data)
        actual (:actual data)
        passed (cond
                 (= event :fail) (= msg "Should fail")
                 (= event :pass) (= msg "Should pass")
                 (= event :error) (= msg "Should error")
                 :else true)]
    (if passed
      (original-report {:type :pass, :message msg,
                        :expected expected, :actual actual})
      (original-report {:type :fail, :message (str msg " but got " event)
                        :expected expected, :actual actual}))))

;; test-ns-hook will be used by test/test-ns to run tests in this
;; namespace.
(defn test-ns-hook []
  (binding [original-report clojure.test/report
            report/report custom-report]
    (#'t/test-all-vars (find-ns 'circleci.test-test))))

(deftest clj-1588-symbols-in-are-isolated-from-test-clauses
  (binding [clojure.test/report original-report]
    (are [x y] (= x y)
      ((fn [x] (inc x)) 1) 2)))

(deftest dummy-test
  (is 1 "Should pass"))

(defn tracking-report
  [reports]
  (fn [data]
    (swap! reports conj data)))

(deftest nested-test-invocations-use-correct-test-var
  (let [reports (atom [])]
    (binding [clojure.test/report (tracking-report reports)]
      (dummy-test))

    (let [end-test-var-data (->> @reports
                                 (filter #(-> % :type (= :end-test-var)))
                                 first)]
      (is (some? (:elapsed end-test-var-data)) "Should pass"))))

(defn counting-fixture
  [counter]
  (fn [f]
    (swap! counter inc)
    (f)))


;; Dummy ns for testing once fixtures
(in-ns 'circleci.test.test-ns)
(clojure.core/require '[clojure.test :refer (deftest is)])

(deftest dummy-test
  (is 1 "Should pass"))

(deftest nested-dummy-test
  (dummy-test))

;; And back to circleci.test-test
(in-ns 'circleci.test-test)


(deftest once-fixture-fns-run-exactly-once-for-test-var-invocations
  (let [reports (atom [])
        test-ns (find-ns 'circleci.test.test-ns)
        once-fixture-counts (atom 0)
        _ (alter-meta! test-ns
                       assoc ::clojure.test/once-fixtures
                             [(counting-fixture once-fixture-counts)])
        test-fn (ns-resolve test-ns 'nested-dummy-test)]
    (binding [clojure.test/report (tracking-report reports)]
      (test-fn))

    (is (= 1 @once-fixture-counts) "Should pass")
    (is (= 2 (->> @reports
                  (filter #(-> % :type (= :begin-test-var)))
                  count))
        "Should pass")
    (is (= 2 (->> @reports
                  (filter #(-> % :type (= :end-test-var)))
                  count))
        "Should pass")))

(deftest once-fixture-fns-run-exactly-once-for-test-ns-invocations
  (let [reports (atom [])
        test-ns (find-ns 'circleci.test.test-ns)
        once-fixture-counts (atom 0)
        _ (alter-meta! test-ns
                       assoc ::clojure.test/once-fixtures
                             [(counting-fixture once-fixture-counts)])]
    (binding [report/report (tracking-report reports)]
      (t/test-ns test-ns))

    (is (= 1 @once-fixture-counts) "Should pass")
    (is (= 3 (->> @reports
                  (filter #(-> % :type (= :begin-test-var)))
                  count))
        "Should pass")
    (is (= 3 (->> @reports
                  (filter #(-> % :type (= :end-test-var)))
                  count))
        "Should pass")
    (is (= 1 (->> @reports
                  (filter #(-> % :type (= :begin-test-ns)))
                  count))
        "Should pass")
    (is (= 1 (->> @reports
                  (filter #(-> % :type (= :end-test-ns)))
                  count))
        "Should pass")))
