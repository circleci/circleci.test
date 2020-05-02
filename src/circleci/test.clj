(ns circleci.test
  (:require [circleci.test.report :as report]
            [clojure.test :as test]
            [clojure.string :as cs]
            [clojure.java.io :as io])
  (:import (clojure.lang LineNumberingPushbackReader)
           java.io.FileNotFoundException))

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
;; Keep track of whether for you are inside a form which has already
;; run the fixture you want. That is, no chain of function calls
;; should have a fixture twice.
(def ^:dynamic *fixtures* {})

(def ^:private default-config {:test-results-dir "test-results"
                               :reporters [report/clojure-test-reporter]})

(defn read-config! []
  (let [config (if-let [r (io/resource "circleci_test/config.clj")]
                 (with-open [rdr (LineNumberingPushbackReader. (io/reader r))]
                  (load-reader rdr))
                 {})]
    (merge default-config config)))

(defn- make-once-fixture
  [ns]
  (fn [f]
    (if (get-in *fixtures* [:once ns])
      (f)
      (binding [*fixtures* (assoc-in *fixtures* [:once ns] true)]
        (let [fix-fn (test/join-fixtures (::test/once-fixtures (meta ns)))]
          (fix-fn f))))))

(defn- make-each-fixture
  [ns]
  (fn [f]
    (if (get-in *fixtures* [:each ns])
      (f)
      (binding [*fixtures* (assoc-in *fixtures* [:each ns] true)]
        (let [fix-fn (test/join-fixtures (::test/each-fixtures (meta ns)))]
          (fix-fn f))))))

(defn make-global-fixture
  [{:keys [global-fixture]
    :or {global-fixture (fn [f] (f))}}]
  (fn [f]
    (if (get *fixtures* :global)
      (f)
      (binding [*fixtures* (assoc *fixtures* :global true)]
        (global-fixture f)))))

;; Running tests; low-level fns

(defn- get-reporters [config]
  (or report/*reporters*
      (for [make-reporter (:reporters config)]
        (make-reporter config))))

(defn- nanos->seconds
  [nanos]
  (/ nanos 1e9))

(defn- test*
  [v]
  (when-let [t (:test (meta v))]
    (test/do-report {:type :begin-test-var, :var v})
    (t)))

(defn- test-var*
  [config v]
  (assert (var? v) (format "v must be a var. got %s" (class v)))
  (let [ns (-> v meta :ns)
        global-fixture-fn (make-global-fixture config)
        once-fixture-fn (make-once-fixture ns)
        each-fixture-fn (make-each-fixture ns)]
    (when (:test (meta v))
      (binding [test/*testing-vars* (conj test/*testing-vars* v)]
        (let [start-time (System/nanoTime)]
          (try
            (global-fixture-fn
             (fn []
               (once-fixture-fn
                (fn []
                  (each-fixture-fn (fn [] (test* v)))))))
            (catch Exception e
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
   (test-var v (read-config!)))
  ([v config]
   ;; Make sure calling any nested test fns invokes _our_ test-var, not
   ;; clojure.test's
   ;;
   ;; Also need to rebind test/report here since test-ns and test-var are
   ;; entrypoints into the test runner
   (binding [test/test-var (partial test-var* config)
             test/report report/report
             report/*reporters* (get-reporters config)]
     (test-var* config v))))


(defn- get-all-vars [config ns selector]
  (for [v (vals (ns-interns ns))
        :when (and (:test (meta v)) (selector (meta v)))]
    v))


(defn- test-all-vars [config ns selector]
  (doseq [v (get-all-vars config ns selector)]
    (test-var v config)))


(defn copy-meta
  [v from-key to-key]
  (if-let [x (get (meta v) from-key)]
    (alter-meta! v #(-> % (assoc to-key x) (dissoc from-key)))))


(defmacro with-unmarking
  [ns var-selector & body]
  `(let [vars# (vals (ns-interns ~ns))]
     (try
       (doseq [a-var# vars#]
         (when (and (:test (meta a-var#))
                    (not (~var-selector a-var#)))
           (copy-meta a-var# :test ::skipped-test)))
       ~@body
       (finally
         (doseq [a-var# vars#]
           (copy-meta a-var# ::skipped-test :test))))))


(defn test-ns
  "The entry-point into circleci.test for running all tests in a namespace.

  If the namespace defines a function named test-ns-hook, calls that.
  Otherwise, calls test-all-vars on the namespace.  'ns' is a
  namespace object or a symbol.

  Internally binds *report-counters* to a ref initialized to
  *initial-report-counters*.  Returns the final, dereferenced state of
  *report-counters*."
  ([ns] (test-ns ns (constantly true)))
  ([ns selector] (test-ns ns selector (read-config!)))
  ([ns selector config]
   (binding [test/*report-counters* (ref test/*initial-report-counters*)
             test/report report/report
             report/*reporters* (get-reporters config)]
     (let [ns-obj (the-ns ns)
           run-once-fixture? (seq (get-all-vars config ns selector))
           global-fixture-fn (make-global-fixture config)
           once-fixture-fn (if run-once-fixture?
                             (make-once-fixture ns-obj)
                             (fn [f] (f)))]
       (try
         (global-fixture-fn
           (fn []
             (once-fixture-fn
               (fn []
                 (test/do-report {:type :begin-test-ns, :ns ns-obj})
                 ;; If the namespace has a test-ns-hook function, call that:
                 (if-let [v (find-var (symbol (str (ns-name ns-obj))
                                              "test-ns-hook"))]
                   (with-unmarking ns
                     (fn [a-var] (-> a-var meta selector))
                     (binding [test/test-var (partial test-var* config)]
                       ((var-get v))))

                   ;; Otherwise, just test every var in the namespace.
                   (test-all-vars config ns-obj selector))))))
         (catch Exception e
           (binding [test/*testing-vars*
                     (conj test/*testing-vars* (with-meta 'test
                                                          {:name "Exception thrown from test fixture"
                                                           :ns ns-obj}))]
             (test/do-report {:type :error,
                              :message "Exception thrown from test fixture."
                              :expected nil, :actual e})))
         (finally
           (test/do-report {:type :end-test-ns, :ns ns-obj}))))
     @test/*report-counters*)))

(defn test-nses
  [nses selector config]
  (for [n (sort-by str (distinct nses))]
    (test-ns n selector config)))

(defn test-selected-var
  [v selector config]
  (binding [test/*report-counters* (ref test/*initial-report-counters*)
            test/report report/report
            report/*reporters* (get-reporters config)]
    (let [n (-> v meta :ns)]
      (if-let [tnv (find-var (symbol (str (ns-name n))
                                     "test-ns-hook"))]
        (with-unmarking n
          (fn [a-var] (and (= a-var v) (-> a-var meta selector)))
          (binding [test/test-var (partial test-var* config)]
            ((var-get tnv))))

        (when (-> v meta selector)
          (binding [test/test-var (partial test-var* config)]
            (test-var* config v)))))
    @test/*report-counters*))


(defn test-vars-in-ns-groups
  [vars selector config]
  (let [ns-groups (group-by (comp :ns meta) (distinct vars))
        reports (for [ns (keys ns-groups)]
                  (try
                    (test/do-report {:type :begin-test-ns, :ns ns})
                    (for [v (get ns-groups ns)]
                      (test-selected-var v selector config))
                    (finally (test/do-report {:type :end-test-ns, :ns ns}))))]
    (apply concat reports)))


;; Running tests; high-level fns

(defn- run-selected-tests
  "Runs tests filtered by selector function in given namespace; prints results.
  Defaults to current namespace if none given.  Returns a map
  summarizing test results."
  ([selector] (run-selected-tests selector [*ns*]))
  ([selector nses] (run-selected-tests selector nses []))
  ([selector nses vars] (run-selected-tests selector nses vars (read-config!)))
  ([selector nses vars config]
   (if (and (empty? nses) (empty? vars))
     (let [summary {:type :summary :pass 0 :fail 0 :error 0 :test 0}]
       (test/do-report summary)
       summary)

     (let [global-fixture-fn (make-global-fixture config)
           summary (global-fixture-fn
                    #(assoc (apply merge-with + {:pass 0 :fail 0 :error 0 :test 0}
                                   (into
                                    (test-nses nses selector config)
                                    (test-vars-in-ns-groups vars selector config)))
                            :type :summary))]

       (test/do-report summary)
       summary))))

(defn run-tests
  "Runs all tests in the given namespaces; prints results.
  Defaults to current namespace if none given.  Returns a map
  summarizing test results."
  ([] (run-tests *ns*))
  ([& namespaces] (run-selected-tests (constantly true) namespaces)))

(defn run-all-tests
  "Runs all tests in all loaded namespaces; prints results.
  Optional argument is a regular expression; only namespaces with
  names matching the regular expression (with re-matches) will be
  tested. Note that this may skip tests which exist on disk but haven't
  been loaded yet."
  ([] (apply run-tests (all-ns)))
  ([re] (apply run-tests (filter #(re-matches re (name (ns-name %))) (all-ns)))))

(defn- lookup-selector [config selector-name]
  (let [selectors (merge {:default identity}
                         (:selectors config))]
    (or (get selectors selector-name)
        (throw (Exception. (str "Selector not found: " selector-name))))))

(defn- read-args [config raw-args]
  (let [args (map read-string raw-args)]
    (if (keyword? (first args)) ; the selector must be the first arg
      (cons (lookup-selector config (first args)) (rest args))
      (cons (lookup-selector config :default) args))))

(defn- nses-in-directories [dirs]
  (for [dir dirs f (file-seq (io/file dir))
        :when (re-find #"\.cljc?$" (str f))]
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
         selector (lookup-selector (read-config!) (read-string selector-str))
         summary (run-selected-tests selector nses)]
     (System/exit (+ (:error summary) (:fail summary))))))


(defn segregate-nses-and-vars
  "Given a list of namespaces and vars, separates them into namespaces
  which are to be tested fully and individual vars. Also loads all
  required namespaces."
  [nses-or-vars]
  (let [{nses false vars true} (group-by #(cs/includes? (str %) "/") nses-or-vars)
        nses-set (set nses)
        nses-from-vars (mapv #(symbol (first (cs/split (str %) #"/"))) vars)
        test-nses (distinct (into nses nses-from-vars))
        whole-nses (filterv (fn [ns]
                              (try (require :reload ns)
                                   (nses-set ns)
                                   (catch FileNotFoundException _)))
                            test-nses)
        extra-vars (keep (fn [v]
                           (try
                             (when-let [v (find-var (symbol v))]
                               (when-not (-> v meta :ns ns-name nses-set)
                                 v))
                             (catch IllegalArgumentException _)))
                         vars)]
    [whole-nses extra-vars]))

(defn -main
  [& raw-args]
  (when (empty? raw-args)
    (throw (ex-info "Must pass a list of namespaces to test" {})))
  (let [config (read-config!)
        [selector & nses-or-vars] (read-args config raw-args)
        [nses vars] (segregate-nses-and-vars nses-or-vars)
        summary (run-selected-tests selector nses vars config)]
    (System/exit (+ (:error summary) (:fail summary)))))
