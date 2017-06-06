(ns circleci.test
  (:require [circleci.test.report :as report]
            [clojure.test :as test]
            [clojure.java.io :as io]))

;; Once fixtures should be run exactly once regardless of which entry-point
;; into circleci.test is used
;;
;; The two entry-points are test-ns and test-var
;; For test-ns the once fixtures should be run before any tests in the ns are
;; run.
;; For test-var the once fixtures should be run before the test is run.
;;
;; This means test-var must attempt to run once fixtures each time it is
;; invoked.
;;
;; To avoid constantly recalculating once fixtures we map the ns to a
;; fixture-generating function.
;; When running once fixtures we look up the fixture generating fn and invoke
;; it to produce the once-fixtures-fn.
;; Before invoking the generated once-fixtures-fn we bind a new mapping from
;; the ns the test var belongs to to a fixture generating fn that produces a
;; dummy once-fixture.
(def ^:dynamic *once-fixtures* {})

(defn- read-config! []
  (if-let [r (io/resource "circleci_test/config.clj")]
    (load-reader (clojure.lang.LineNumberingPushbackReader. (io/reader r)))
    {}))

(defn- make-once-fixture-fn
  [ns]
  (test/join-fixtures (::test/once-fixtures (meta ns))))

(defn- once-fixtures
  [ns]
  (fn [f]
    (let [make-once-fixtures (get *once-fixtures* ns make-once-fixture-fn)]
      (binding [*once-fixtures* (assoc *once-fixtures*
                                       ns
                                       (fn [_]
                                        (fn [x] (x))))]
        ((make-once-fixtures ns) f)))))

;; Running tests; low-level fns

(defn- nanos->seconds
  [nanos]
  (/ nanos 1e9))

(defn- test*
  [v]
  (when-let [t (:test (meta v))]
    (test/do-report {:type :begin-test-var, :var v})
    (t)))

(defn- test-var*
  [v]
  (assert (var? v) (format "v must be a var. got %s" (class v)))
  (let [ns (-> v meta :ns)
        once-fixture-fn (once-fixtures ns)
        each-fixture-fn (test/join-fixtures (::test/each-fixtures (meta ns)))]
    (when (:test (meta v))
      (binding [test/*testing-vars* (conj test/*testing-vars* v)]
        (let [start-time (System/nanoTime)]
          (try
            (once-fixture-fn
              (fn []
                (each-fixture-fn (fn [] (test* v)))))
            (catch Throwable e
              (test/do-report {:type :error,
                               :message "Uncaught exception, not in assertion."
                               :expected nil, :actual e}))
            (finally
              (let [stop-time (System/nanoTime)
                    elapsed (-> stop-time (- start-time) nanos->seconds)]
                (test/do-report {:type :end-test-var,
                                 :var v
                                 :elapsed elapsed})))))))))

(defn test-var
  "The entry-point into circleci.test for running a single test.

  This could be by invoking a deftest directly from a repl, editor integration
  etc."
  ([v]
   (test-var (read-config!) v))
  ([config v]
   ;; Make sure calling any nested test fns invokes _our_ test-var, not
   ;; clojure.test's
   ;;
   ;; Also need to rebind test/report here since test-ns and test-var are
   ;; entrypoints into the test runner
   (binding [test/test-var test-var*
             test/report report/report
             report/*reporters* (:reporters config report/*reporters*)]
     (test-var* v))))


(defn- test-all-vars [config ns selector]
  (doseq [v (vals (ns-interns ns))]
    (when (and (:test (meta v)) (selector (meta v)))
      (test-var config v))))

(defn test-ns
  "The entry-poing into circleci.test for running all tests in a namespace.

  If the namespace defines a function named test-ns-hook, calls that.
  Otherwise, calls test-all-vars on the namespace.  'ns' is a
  namespace object or a symbol.

  Internally binds *report-counters* to a ref initialized to
  *initial-report-counters*.  Returns the final, dereferenced state of
  *report-counters*."
  ([ns] (test-ns ns (constantly true)))
  ([ns selector] (test-ns (read-config!) ns selector))
  ([config ns selector]
   (binding [test/*report-counters* (ref test/*initial-report-counters*)
             test/report report/report
             report/*reporters* (:reporters config report/*reporters*)]
     (let [ns-obj (the-ns ns)
           once-fixture-fn (once-fixtures ns-obj)]
       (once-fixture-fn
        (fn []
          (test/do-report {:type :begin-test-ns, :ns ns-obj})
          ;; If the namespace has a test-ns-hook function, call that:
          (if-let [v (find-var (symbol (str (ns-name ns-obj)) "test-ns-hook"))]
            ((var-get v))
            ;; Otherwise, just test every var in the namespace.
            (test-all-vars config ns-obj selector))
          (test/do-report {:type :end-test-ns, :ns ns-obj}))))
     @test/*report-counters*)))


;; Running tests; high-level fns

(defn- run-selected-tests
  "Runs tests filtered by selector function in given namespace; prints results.
  Defaults to current namespace if none given.  Returns a map
  summarizing test results."
  ([selector] (run-selected-tests (read-config!) selector *ns*))
  ([config selector & namespaces]
   (let [summary (assoc (apply merge-with + (for [n namespaces]
                                              (test-ns config n selector)))
                        :type :summary)]
     (test/do-report summary)
     summary)))

(defn run-tests
  "Runs all tests in the given namespaces; prints results.
  Defaults to current namespace if none given.  Returns a map
  summarizing test results."
  ([] (run-tests *ns*))
  ([& namespaces] (apply run-selected-tests (constantly true) namespaces)))

(defn run-all-tests
  "Runs all tests in all loaded namespaces; prints results.
  Optional argument is a regular expression; only namespaces with
  names matching the regular expression (with re-matches) will be
  tested. Note that this may skip tests which exist on disk but haven't
  been loaded yet."
  ([] (apply run-tests (all-ns)))
  ([re] (apply run-tests (filter #(re-matches re (name (ns-name %))) (all-ns)))))

(defn- lookup-selector [config selector-name]
  (let [selectors (:selectors config {:default identity})]
    (or (get selectors selector-name) selector-name)))

(defn- read-args [config raw-args]
  (let [args (map read-string raw-args)]
    (if (keyword? (first args)) ; the selector must be the first arg
      (cons (lookup-selector config (first args)) (rest args))
      (cons (lookup-selector config :default) args))))

(defn- nses-in-directories [dirs]
  (for [dir dirs f (file-seq (io/file dir))
        :when (re-find #"\.clj$" (str f))]
    (second (read-string (slurp f)))))

(defn dir
  ([dirs-str] (dir dirs-str ":default"))
  ([dirs-str selector-str]
   ;; This function is designed to be used with Leiningen aliases only, since
   ;; adding :project/test-dirs to an alias will pass in data from the project
   ;; map as an argument; however it passes it in as a string.
   (when-not (try (coll? (read-string dirs-str)) (catch Exception _))
     (binding [*out* *err*]
       (println "Please see the readme for usage of this function.")
       (System/exit 1)))
   (let [nses (nses-in-directories (read-string dirs-str))
         _ (apply require :reload nses)
         selector (lookup-selector (read-string selector-str))
         summary (apply run-selected-tests selector nses)]
     (System/exit (+ (:error summary) (:fail summary))))))

(defn -main
  [& raw-args]
  (when (empty? raw-args)
    (throw (ex-info "Must pass a list of namespaces to test" {})))
  (let [config (read-config!)
        [selector & nses] (read-args config raw-args)
        _ (apply require :reload nses)
        summary (apply run-selected-tests config selector nses)]
    (System/exit (+ (:error summary) (:fail summary)))))
