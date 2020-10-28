(ns circleci.test.report.cider
  "CIDER-compatible test reporter."
  (:require [circleci.test.report :refer [TestReporter]]
            [cider.nrepl.middleware.test :as cider]))

(deftype CIDERTestReporter []
  TestReporter
  (default [this m]
    (cider/report m))

  (pass [this m]
    (cider/report m))

  (fail [this m]
    (cider/report m))

  (error [this m]
    (cider/report m))

  (summary [this m]
    (cider/report m))

  (begin-test-ns [this m]
    (cider/report m))

  (end-test-ns [this m]
    (cider/report m))

  (begin-test-var [this m]
    (cider/report m))

  (end-test-var [this m]
    (cider/report m)))

(defn reporter [_config]
  (->CIDERTestReporter))
