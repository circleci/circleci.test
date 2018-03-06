(ns circleci.test.report.junit
  "Junit reporter for circleci.test"
  (:require [clojure.data.xml :as xml]
            [clojure.data.xml.node :refer [element?]]
            [clojure.java.io :as io]
            [circleci.test.report :as report]))

(defn- stacktrace->string
  "given an exception, returns the result of printStackTrace as a string"
  [^Throwable e]
  (let [sw (java.io.StringWriter.)
        pw (java.io.PrintWriter. sw)]
    (.printStackTrace e pw)
    (.toString sw)))

(defn- testcase-elems
  [testcases]
  (->> testcases
       (mapcat :content)))

(defn- count-failures
  [testcases]
  (->> (testcase-elems testcases)
       (filter #(= :failure (:tag %)))
       count))

(defn- count-errors
  [testcases]
  (->> (testcase-elems testcases)
       (filter #(= :error (:tag %)))
       count))

(defn- testcase-elapsed
  [testcases]
  (->> testcases
       (map (comp :time :attrs))
       (reduce +)))

(defn- suite-xml
  [m testcases]
  {:pre [(class testcases) (every? element? testcases)]}
  (apply xml/element :testsuite {:name (-> m :ns ns-name str)
                                 :errors (count-errors testcases)
                                 :tests (count testcases)
                                 :failures (count-failures testcases)
                                 :elapsed (testcase-elapsed testcases)}
         testcases))

(defn- test-name
  [v]
  (-> v meta :name str))

(defn- testcase-xml
  "Generate a testcase XML element. Takes the map from a (clojure.test/do-report :end-test-var), and a seq of failure/error reports "
  [m failures]
  (apply xml/element :testcase {:name (-> m :var test-name)
                                :time (-> m :elapsed)}
         failures))

(defn- suite-file-name
  [suite]
  (let [n (-> suite :attrs :name)]
    (format "%s.xml" n)))

(defn- spit-suite
  [suite dir]
  (let [path (io/file dir (suite-file-name suite))]
    (with-open [f (java.io.FileWriter. path)]
      (xml/emit suite f))))

(defn- failure-xml
  [m]
  (xml/sexp-as-element [:failure {:type "assertion failure"
                                  :message (format "expected: %s. actual: %s" (:expected m) (:actual m))} ""]))

(defn- error-xml
  [m]
  (xml/sexp-as-element [:error {:type (-> m :actual (.getClass))
                                :message (-> m :actual (.getMessage))}
                        (-> m :actual stacktrace->string)]))

(deftype JunitReporter [out-dir suite-testcases test-failures]
  report/TestReporter
  (default [this m])

  (pass [this m])

  (fail [this m]
    (swap! test-failures conj (failure-xml m)))

  (error [this m]
    (swap! test-failures conj (error-xml m)))

  (summary [this m])

  (begin-test-ns [this m]
    (reset! suite-testcases []))

  (end-test-ns [this m]
    (.mkdirs (java.io.File. out-dir))
    (spit-suite (suite-xml m @suite-testcases) out-dir))

  (begin-test-var [this m]
    (reset! test-failures []))

  (end-test-var [this m]
    (let [testcase (testcase-xml m @test-failures)]
      (when-not suite-testcases
        (throw (ex-info "end-test outside of testcases" {})))
      (swap! suite-testcases conj testcase))))

(defn reporter
  "Create a TestReporter that writes Junit compatible XML reports to
  `out-dir`."
  [config]
  (->JunitReporter (:test-results-dir config) (atom []) (atom [])))
