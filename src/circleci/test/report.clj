;; This is all copied from clojure.test
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


;; Test result reporting
(defmulti
  ^{:doc "Generic reporting function, may be overridden to plug in
   different report formats (e.g., TAP, JUnit).  Assertions such as
   'is' call 'report' to indicate results.  The argument given to
   'report' will be a map with a :type key.  See the documentation at
   the top of test_is.clj for more information on the types of
   arguments for 'report'."
     :dynamic true
     :added "1.1"}
  report :type)

(defmethod report :default [m]
  (test/with-test-out (prn m)))

(defmethod report :pass [m]
  (test/with-test-out (test/inc-report-counter :pass)))

(defmethod report :fail [m]
  (test/with-test-out
    (test/inc-report-counter :fail)
    (println "\nFAIL in" (testing-vars-str m))
    (when (seq test/*testing-contexts*) (println (testing-contexts-str)))
    (when-let [message (:message m)] (println message))
    (println "expected:" (pr-str (:expected m)))
    (println "  actual:" (pr-str (:actual m)))))

(defmethod report :error [m]
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

(defmethod report :summary [m]
  (test/with-test-out
   (println "\nRan" (:test m) "tests containing"
            (+ (:pass m) (:fail m) (:error m)) "assertions.")
   (println (:fail m) "failures," (:error m) "errors.")))

(defmethod report :begin-test-ns [m]
  (test/with-test-out
   (println "\nTesting" (ns-name (:ns m)))))

;; Ignore these message types:
(defmethod report :end-test-ns [m])
(defmethod report :begin-test-var [m])
(defmethod report :end-test-var [m])
