(ns weasley.sample-test-ns1
  (:require [clojure.test :refer :all]))

(def ^:dynamic vonce 0)
(def ^:dynamic veach 0)
(def ^:dynamic vcommon 0)

(deftest test-1
  (println "Running: circleci.sample-test-ns1/test-1")
  (is (= 1 1))
  (is (zero? veach))
  (is (zero? vonce))
  (is (zero? vcommon)))

(deftest test-2
  (println "Running: circleci.sample-test-ns1/test-2")
  (is (= 10 10))
  (is (zero? veach))
  (is (zero? vonce))
  (is (zero? vcommon)))

(deftest test-3
  (println "Running: circleci.sample-test-ns1/test-3")
  (is (= 109 109))
  (is (zero? veach))
  (is (zero? vonce))
  (is (zero? vcommon)))


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
