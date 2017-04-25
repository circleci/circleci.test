(ns circleci.test.test-report
  (:require [clojure.test :refer (deftest testing is do-report)]
            [circleci.test.report :as report]))

(deftype TestTestReporter [reports counters]
  report/TestReporter
  (default [this m]
    (swap! reports update :default (fnil conj []) m))

  (pass [this m]
    (swap! reports update :pass (fnil conj []) m)
    (swap! counters update :pass (fnil inc 0)))

  (fail [this m]
    (swap! reports update :fail (fnil conj []) m)
    (swap! counters update :fail (fnil inc 0)))

  (error [this m]
    (swap! reports update :error (fnil conj []) m)
    (swap! counters update :error (fnil inc 0)))

  (summary [this m]
    (swap! reports update :summary (fnil conj []) m))

  (begin-test-ns [this m]
    (swap! reports update :begin-test-ns (fnil conj []) m))

  ;; Ignore these message types:
  (end-test-ns [this m]
    (swap! reports update :end-test-ns (fnil conj []) m))

  (begin-test-var [this m]
    (swap! reports update :begin-test-var (fnil conj []) m))

  (end-test-var [this m]
    (swap! reports update :end-test-var (fnil conj []) m)))

(deftest reporters-work
  (let [reports (atom {})
        counters (atom {})
        exception (ex-info "test exception" {})]
    (binding [report/*reporters* [(->TestTestReporter reports counters)]
              clojure.test/report report/report]
      (do-report {:type :default})
      (do-report {:type :pass, :message "msg", :expected "expected", :actual "actual"})
      (do-report {:type :fail, :message "msg", :expected "expected", :actual "actual"})
      (do-report {:type :error, :message "msg", :expected "expected", :actual exception})
      (do-report {:type :summary, :pass 1, :fail 2, :error 3})
      (do-report {:type :begin-test-ns, :ns "namespace"})
      (do-report {:type :end-test-ns, :ns "namespace"})
      (do-report {:type :begin-test-var, :var "Var"})
      (do-report {:type :end-test-var, :var "Var"}))

    (is (= {:default        [{:type :default}]
            :pass           [{:type :pass, :message "msg", :expected "expected", :actual "actual"}]
            :fail           [{:file "test_report.clj", :line 46, :type :fail, :message "msg", :expected "expected", :actual "actual"}],
            :error          [{:file "core.clj", :line 4617, :type :error, :message "msg", :expected "expected", :actual exception}]
            :summary        [{:type :summary, :pass 1, :fail 2, :error 3}]
            :begin-test-ns  [{:type :begin-test-ns, :ns "namespace"}]
            :end-test-ns    [{:type :end-test-ns, :ns "namespace"}]
            :begin-test-var [{:type :begin-test-var, :var "Var"}]
            :end-test-var   [{:type :end-test-var, :var "Var"}]}
           @reports))

    (is (= {:pass 1, :fail 1, :error 1} @counters))))

(deftest ^:failing test-failing-metadata-skips
  (is false))
