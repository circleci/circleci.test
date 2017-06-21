(ns circleci.test.retest
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [circleci.test :as test]))

(defn- failed-tests-from [root]
  (let [suite (:name (:attrs root))]
    (for [test-case (:content root)
          :when (some #(#{:failure :error} (:tag %)) (:content test-case))]
      (symbol suite (:name (:attrs test-case))))))

(def ^:private missing-junit-msg
  "Previous test report files not found. Is the junit reporter configured?")

(defn- failed-tests [report-dir]
  (when-not (.exists (io/file report-dir))
    (throw (Exception. missing-junit-msg)))
  (for [report-file (.listFiles (io/file report-dir))
        test (failed-tests-from (xml/parse (io/reader report-file)))]
    test))

(defn- make-selector [tests]
  (comp (set (map name tests)) str :name))

(defn -main []
  (let [{:keys [test-results-dir] :as config} (test/read-config!)]
    (when-not (.exists (io/file test-results-dir))
      (binding [*out* *err*]
        (println missing-junit-msg)
        (System/exit 1)))
    (if-let [failed (seq (failed-tests test-results-dir))]
      (doseq [[test-ns tests] (group-by (comp symbol namespace) failed)]
        (require test-ns)
        (test/test-ns test-ns (make-selector tests) config))
      (println "No failed tests."))))
