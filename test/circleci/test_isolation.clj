(ns circleci.test-isolation
  (:require [circleci.test :refer :all]
            [clojure.test :refer [deftest is]]))

(def errors (atom []))

;; Dummy ns for testing once fixtures
(in-ns 'circleci.test.under-isolation)
(clojure.core/refer-clojure)
(clojure.core/require '[clojure.test :refer (deftest is use-fixtures)])
(clojure.core/require '[circleci.test.isolation :as i])

(use-fixtures :each (i/enforce))

(deftest test-network
  (try (re-find #"defproject" (slurp "https://leiningen.org"))
       (catch Throwable e
         (swap! circleci.test-isolation/errors conj e))))

(deftest test-octo-inc
  (let [oinc (apply comp (repeat 8 inc))]
    (is (= 9 (oinc 1)))))

(deftest ^:io test-writing-file
  (try
    (spit "/tmp/whatever" "stuff")
    (catch Throwable e
      (swap! circleci.test-isolation/errors conj e))))

(deftest digestive
  (let [digest (java.security.MessageDigest/getInstance "SHA")]
    (is (= [-70 90 59 60 31 -63 58 89 80 -112 67
            -56 101 57 65 -118 -42 10 -101 -118]
           (seq (.digest digest (.getBytes "what are the haps my friends")))))))

;; And back to circleci.test-test
(in-ns 'circleci.test-isolation)

(deftest test-isolation
  (let [summary (atom nil)]
    (with-out-str
      (reset! summary (run-tests 'circleci.test.under-isolation)))
    (is (= 2 (:pass @summary)))
    (is (= [java.lang.SecurityException java.lang.SecurityException]
           (map class @errors)))))
