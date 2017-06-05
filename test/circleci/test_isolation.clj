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

;; And back to circleci.test-test
(in-ns 'circleci.test-isolation)

(deftest test-isolation
  ;; Work around problem with cached class lookups; see #9
  (java.security.MessageDigest/getInstance "SHA")
  (let [summary (atom nil)]
    (with-out-str
      (reset! summary (run-tests 'circleci.test.under-isolation)))
    (is (= 1 (:pass @summary)))
    (is (= [java.lang.SecurityException java.lang.ExceptionInInitializerError]
           (map class @errors)))))
