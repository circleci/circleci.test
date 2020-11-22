(ns circleci.sample-test-ns2
  (:require [clojure.test :refer :all]))

(def ^:dynamic vonce 0)
(def ^:dynamic veach 0)
(def ^:dynamic vcommon 0)
(def ^:dynamic level-of-nesting 0)

(deftest test-1
  (when (zero? level-of-nesting)
    (println "Running: circleci.sample-test-ns2/test-1"))
  (is (= 1 1))
  (is (= 1 veach))
  (is (= 1 vonce))
  (is (= 2 vcommon)))

(deftest test-2
  (when (zero? level-of-nesting)
    (println "Running: circleci.sample-test-ns2/test-2"))
  (is (= 10 10))
  (is (= 1 veach))
  (is (= 1 vonce))
  (is (= 2 vcommon)))

(deftest test-3
  (when (zero? level-of-nesting)
    (println "Running: circleci.sample-test-ns2/test-3"))
  (is (= 109 109))
  (is (= 1 veach))
  (is (= 1 vonce))
  (is (= 2 vcommon)))

(deftest ^:combination test-4
  (when (zero? level-of-nesting)
    (println "Running: circleci.sample-test-ns2/test-4"))
  (binding [level-of-nesting (inc level-of-nesting)]
    (test-1)
    (test-2)
    (test-3)))

(deftest ^:combination test-5
  (println "Running: circleci.sample-test-ns2/test-5")
  (binding [level-of-nesting (inc level-of-nesting)]
    (test-1)
    (test-2)
    (test-3)
    (test-4)))


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
