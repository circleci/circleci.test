(ns circleci.sample-test-ns1
  (:require [clojure.test :refer :all]))


(deftest test-1
  (println "Running: circleci.sample-test-ns1/test-1")
  (is (= 1 1)))

(deftest test-2
  (println "Running: circleci.sample-test-ns1/test-2")
  (is (= 10 10)))

(deftest test-3
  (println "Running: circleci.sample-test-ns1/test-3")
  (is (= 109 109)))


(defn test-ns-hook
  []
  (test-1)
  (test-2)
  (test-3))
