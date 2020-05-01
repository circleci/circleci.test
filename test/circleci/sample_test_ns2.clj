(ns circleci.sample-test-ns2
  (:require [clojure.test :refer :all]))


(deftest test-1
  (println "Running: circleci.sample-test-ns2/test-1")
  (is (= 1 1)))

(deftest test-2
  (println "Running: circleci.sample-test-ns2/test-2")
  (is (= 10 10)))

(deftest test-3
  (println "Running: circleci.sample-test-ns2/test-3")
  (is (= 109 109)))
