;; Portions copied from clojure.test

(ns circleci.test.report
  (:require [clojure.stacktrace :as stack]
            [clojure.test :as test]))

(defn ^:redef testing-vars-str
  "Returns a string representation of the current test.  Renders names
  in *testing-vars* as a list, then the source file and line of
  current assertion."
  [m]
  (let [{:keys [file line]} m]
    (str
     (ns-name (:ns (meta (first test/*testing-vars*)))) "/ "
     (reverse (map #(:name (meta %)) test/*testing-vars*))
     " (" file ":" line ")")))

(defn ^:redef testing-contexts-str
  "Returns a string representation of the current test context. Joins
  strings in *testing-contexts* with spaces."
  []
  (apply str (interpose " " (reverse test/*testing-contexts*))))


(defprotocol TestReporter
  (default [this m])
  (pass [this m])
  (fail [this m])
  (error [this m])
  (summary [this m])
  (begin-test-ns [this m])
  (end-test-ns [this m])
  (begin-test-var [this m])
  (end-test-var [this m]))

(deftype ClojureDotTestReporter []
  TestReporter
  (default [this m]
    (test/with-test-out (prn m)))

  (pass [this m]
    (test/with-test-out (test/inc-report-counter :pass)))

  (fail [this m]
    (test/with-test-out
      (test/inc-report-counter :fail)
      (println "\nFAIL in" (testing-vars-str m))
      (when (seq test/*testing-contexts*) (println (testing-contexts-str)))
      (when-let [message (:message m)] (println message))
      (println "expected:" (pr-str (:expected m)))
      (println "  actual:" (pr-str (:actual m)))))

  (error [this m]
    (test/with-test-out
      (test/inc-report-counter :error)
      (println "\nERROR in" (testing-vars-str m))
      (when (seq test/*testing-contexts*) (println (testing-contexts-str)))
      (when-let [message (:message m)] (println message))
      (println "expected:" (pr-str (:expected m)))
      (print "  actual: ")
      (let [actual (:actual m)]
        (if (instance? Throwable actual)
          (stack/print-cause-trace actual test/*stack-trace-depth*)
          (prn actual)))))

  (summary [this m]
    (test/with-test-out
      (println "\nRan" (:test m) "tests containing"
               (+ (:pass m) (:fail m) (:error m)) "assertions.")
      (println (:fail m) "failures," (:error m) "errors.")))

  (begin-test-ns [this m]
    (test/with-test-out
      (println "\nTesting" (ns-name (:ns m)))))

  (end-test-ns [this m])

  (begin-test-var [this m]
    (test/with-test-out
      (test/inc-report-counter :test)))

  (end-test-var [this m]))

(defn clojure-test-reporter [_config]
  (->ClojureDotTestReporter))

(def ^:dynamic *reporters*
  "The sequence of currently configured test reporters. Consumers of the
  circleci.test library should not bind this var, set the :reporters key in
  test runner config instead."
  nil)

;; Test result reporting
(defmulti
  ^{:doc "Reporting function that supports multiple reporting backends for
   different output formats. To override the default console reporter bind
   '*reporters*' to a seq of 'TestReporter' implementations.
   Assertions such as 'is' call 'report' to indicate results.  The argument
   given to 'report' will be a map with a :type key.  See the documentation at
    the top of test_is.clj for more information on the types of
    arguments for 'report'."
     :dynamic true}
  report :type)

(defmethod report :default [m]
  (doseq [reporter *reporters*]
    (default reporter m)))

(defmethod report :pass [m]
  (doseq [reporter *reporters*]
    (pass reporter m)))

(defmethod report :fail [m]
  (doseq [reporter *reporters*]
    (fail reporter m)))

(defmethod report :error [m]
  (doseq [reporter *reporters*]
    (error reporter m)))

(defmethod report :summary [m]
  (doseq [reporter *reporters*]
    (summary reporter m)))

(defmethod report :begin-test-ns [m]
  (doseq [reporter *reporters*]
    (begin-test-ns reporter m)))

(defmethod report :end-test-ns [m]
  (doseq [reporter *reporters*]
    (end-test-ns reporter m)))

(defmethod report :begin-test-var [m]
  (doseq [reporter *reporters*]
    (begin-test-var reporter m)))

(defmethod report :end-test-var [m]
  (doseq [reporter *reporters*]
    (end-test-var reporter m)))
