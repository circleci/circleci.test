(ns circleci.test.cloverage
  "Support for Cloverage test coverage tool."
  (:require [cloverage.coverage :as cov]
            [circleci.test :as test]))

;; This is a copy of the clojure.test runner which ships with cloverage
;; but with clojure.test swapped out with circleci.test's run-tests.
(defmethod cov/runner-fn :circleci.test [{}]
  (fn [nses]
    (apply require (map symbol nses))
    {:errors (reduce + ((juxt :error :fail)
                        (apply test/run-tests nses)))}))
