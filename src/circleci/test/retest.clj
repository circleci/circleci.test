(ns circleci.test.retest
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [circleci.test :as test]))

(defn- failed-tests-from [root]
  (let [suite (:name (:attrs root))]
    (for [test-case (:content root)
          :when (some #(= :failure (:tag %)) (:content test-case))]
      (symbol suite (:name (:attrs test-case))))))

(def ^:private missing-junit-msg
  "Previous test report files not found. Is the junit reporter configured?")

(defn- failed-tests [report-dir]
  (when-not (.exists (io/file report-dir))
    (throw (Exception. missing-junit-msg)))
  (for [report-file (.listFiles (io/file report-dir))
        test (failed-tests-from (xml/parse (io/reader report-file)))]
    test))

(defn -main [report-dir]
  (if (.exists (io/file report-dir))
    (doseq [test (failed-tests report-dir)]
      (require (symbol (namespace test)))
      (test/test-var (resolve test)))
    (binding [*out* *err*]
      (println missing-junit-msg)
      (System/exit 1))))
