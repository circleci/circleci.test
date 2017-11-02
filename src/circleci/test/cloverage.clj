(ns circleci.test.cloverage
  "Support for Cloverage test coverage tool."
  (:require [cloverage.coverage :as cov]
            [circleci.test :as test]))

(defmethod cov/runner-fn :circleci.test [{}]
  (fn [nses]
    (apply require (map symbol nses))
    {:errors (reduce + ((juxt :error :fail)
                        (apply test/run-tests nses)))}))
