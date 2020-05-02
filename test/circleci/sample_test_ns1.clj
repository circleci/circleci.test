(ns circleci.sample-test-ns1
  (:require [clojure.test :refer :all]))

(def ^:dynamic vonce 0)
(def ^:dynamic veach 0)
(def ^:dynamic vcommon 0)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; This namespace's tests will fail with the Leiningen test runner ;;
;; because when there is a test-ns-hook, fixtures are NOT run by   ;;
;; Leiningen or Clojure.test                                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest test-1
  (println "Running: circleci.sample-test-ns1/test-1")
  (is (= 1 1))
  (is (= 1 veach))
  (is (= 1 vonce))
  (is (= 2 vcommon)))

(deftest test-2
  (println "Running: circleci.sample-test-ns1/test-2")
  (is (= 10 10))
  (is (= 1 veach))
  (is (= 1 vonce))
  (is (= 2 vcommon)))

(deftest test-3
  (println "Running: circleci.sample-test-ns1/test-3")
  (is (= 109 109))
  (is (= 1 veach))
  (is (= 1 vonce))
  (is (= 2 vcommon)))


(defn test-ns-hook
  []
  (test-1)
  (test-2)
  (test-3))


(defn each-fixture
  [f]
  (binding [veach (inc veach)
            vcommon (inc vcommon)]
    (f)))


(defn once-fixture
  [f]
  (binding [vonce (inc vonce)
            vcommon (inc vcommon)]
    (f)))



(use-fixtures :each each-fixture)
(use-fixtures :once once-fixture)
