(ns ^:global-fixture weasley.sample-test-ns3
  (:require [clojure.test :refer :all]))

(def ^:dynamic vonce 0)
(def ^:dynamic veach 0)
(def ^:dynamic vcommon 0)
(def ^:dynamic level-of-nesting 0)

(defn global-fixture-file
  []
  (slurp "global_fixture_test.out"))

(deftest test-1
  (when (zero? level-of-nesting)
    (println "Running: circleci.sample-test-ns3/test-1"))
  (is (= 1 veach))
  (is (= 1 vonce))
  (is (= 2 vcommon))
  (is (= "1" (global-fixture-file))))

(deftest test-2
  (when (zero? level-of-nesting)
    (println "Running: circleci.sample-test-ns3/test-2"))
  (is (= 1 veach))
  (is (= 1 vonce))
  (is (= 2 vcommon))
  (is (= "1" (global-fixture-file))))

(deftest test-3
  (when (zero? level-of-nesting)
    (println "Running: circleci.sample-test-ns3/test-3"))
  (is (= 1 veach))
  (is (= 1 vonce))
  (is (= 2 vcommon))
  (is (= "1" (global-fixture-file))))

(deftest ^:combination test-4
  (when (zero? level-of-nesting)
    (println "Running: circleci.sample-test-ns3/test-4"))
  (binding [level-of-nesting (inc level-of-nesting)]
    (test-1)
    (test-2)
    (test-3)
    (assert (= "1" (global-fixture-file)))))

(deftest ^:combination test-5
  (println "Running: circleci.sample-test-ns3/test-5")
  (binding [level-of-nesting (inc level-of-nesting)]
    (test-1)
    (test-2)
    (test-3)
    (test-4)
    (assert (= "1" (global-fixture-file)))))


(defn each-fixture
  [f]
  (binding [veach (inc veach)
            vcommon (inc vcommon)]
    (assert (= "1" (global-fixture-file)))
    (f)))


(defn once-fixture
  [f]
  (binding [vonce (inc vonce)
            vcommon (inc vcommon)]
    (assert (= "1" (global-fixture-file)))
    (f)))


(use-fixtures :each each-fixture)
(use-fixtures :once once-fixture)
