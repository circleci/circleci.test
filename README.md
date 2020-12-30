# circleci.test

A Clojure test-runner compatible with tests written using `clojure.test`.

Keep your existing `deftest`s, but gain flexibility around how you run them.

## Usage

Add `[circleci/circleci.test "0.5.0"]` to your `:dependencies` under `:dev`.

It's recommended to use this set of Leiningen aliases:

```clj
:aliases {"test" ["run" "-m" "circleci.test/dir" :project/test-paths]
          "tests" ["run" "-m" "circleci.test"]
          "retest" ["run" "-m" "circleci.test.retest"]}
```

You can pass a selector to `lein test` (see below) or run just a list of
individual tests with `lein tests my.test.ns my.other.test.ns`.

From inside a repl, you can run all loaded test namespaces with
`(circleci.test/run-tests)` or pass in a list of symbols to just test a few
namespaces. Run individiual tests with `(circleci.test/test-var #'my.test.ns/my-test)`.

### Selectors

As with Leiningen's built-in `test` task, you can tag tests with metadata
that allows you to run just a selection of your tests.

You can put a keyword argument on the command-line invocation before 
specifying which test namespaces to run, and it will cause all `deftest`
forms which aren't tagged with that metadata to be skipped.

    $ lein run -m circleci.test :integration my.first.test.ns

If you need more flexibility in your test selectors you can define arbitrary
selector functions in `dev-resources/circleci_test/config.clj`:

```clj
{:selectors {:all (constantly true)
             :acceptance (fn [m] (or (:integration m) (:functional m)))
             :default (complement :flaky)}}
```

If you have the Leiningen `:aliases` set up as per above, you can pass
in a test selector as a command-line argument:

    $ lein test :acceptance

### Reporters

#### JUnit

In order to gather metadata about your test runs, you can configure
`circleci.test` to emit output
in [JUnit XML format](https://ant.apache.org/manual/Tasks/junitreport.html) by
putting this in your `dev-resources/circleci_test/config.clj` file:

```clj
(require '[circleci.test.report.junit :as junit])

{:test-results-dir "target/test-results"
 :reporters [circleci.test.report/clojure-test-reporter
             junit/reporter]}
```

Unlike `clojure.test`, you can use more than one reporter at a time. It's
recommended to add your `:test-results-dir` directory to the ignored list of
your version control system.

If you use the junit test reporter, you can run `circleci.test.retest/-main` to
re-run only the set of tests which previously failed. Adding an alias in
`project.clj` is recommended:

```clj
:aliases {"test" ["run" "-m" "circleci.test/dir" :project/test-paths]
          "retest" ["run" "-m" "circleci.test.retest"]}
```

#### CIDER

`circleci.test` by default breaks [CIDER](https://cider.mx) test
reports. To avoid this, use these settings in your
`dev-resources/circleci_test/config.clj` to activate the CIDER test
result adapter:

```clojure
(require '[circleci.test.report.cider :as cider])

{:reporters [circleci.test.report/clojure-test-reporter
             cider/reporter]}
```

### Running tests from a repl

Use `circleci.test/test-var` to run a single test fn:
```clojure
(circleci.test/test-var #'my.test.ns/my-test)
```

Use `circleci.test/run-tests` to run all the tests in one or more namespaces:
```clojure
(circleci.test/run-tests 'my.test.ns)
(circleci.test/run-tests 'my.test.ns 'my.other.test.ns)
```

There is also a `circleci.test/run-all-tests` function; however please note that
this only runs tests in namespaces that have already been loaded rather than
running all tests that exist on disk.

### Global fixtures

In addition to `:once` and `:each` fixtures from `clojure.test`, you can define
global fixtures that are only run once for the entire test run, no matter how
many namespaces you run. This should be declared in your config file rather in
any one individual test file though; otherwise it may not get loaded:

```clj
(require '[clojure.spec.test.alpha :as stest])
{:global-fixture (fn [f]
                   (stest/instrument)
                   (myapp.db/migrate)
                   (f))}
```

The global fixture must return the return value of `f`. If you need to do a teardown step you must do something like this:

```clj
(require '[clojure.spec.test.alpha :as stest])
{:global-fixture (fn [f]
                   (stest/instrument)
                   (myapp.db/migrate)
                   (let [results (f)]
                     (teardown)
                     results))}
```

### Test Isolation

Clojure codebases that follow functional programming techniques should follow
the "functional core/imperative shell" model in which most functionality is 
implemented with pure functions, while an outer layer of imperative code ties
things together. Ideally in this case most tests should be unit tests, which
only concern themselves with pure functions and do no I/O. However, it's easy
for impurity to accidentally sneak into a test. In order to prevent this, you
can use the `circleci.test.isolation/enforce` test fixture.

```clj
(use-fixtures :each (isolation/enforce))

(deftest accidentally-uses-io
  (is (re-find #"cx") (get-in (read-parse "sample3") [:body :content 3])))

(def sample3 (read-sample "sample3"))

(deftest actually-pure
  (is (re-find #"cx") (get-in (parse sample3) [:body :content 3])))

(deftest ^:io intentionally-uses-io
  (let [sample3 (read-sample "sample3")
        parsed (parse sample3)]
    (is (re-find #"cx") (get-in parsed [:body :content 3]))))
```

The first test is not a unit test because it calls `read-parse`, a function
that performs I/O. The `isolation/enforce` fixture will cause it to fail, while
the second one will succeed because it only calls `parse`, which is pure. The
third test will also pass because it has explicitly been flagged with `^:io`.

The default isolation blocks use of network and file access and whitelists
`deftest`s which are tagged with `^:io` and `^:integration`. You can pass
arguments to `isolation/enforce` which will provide a fixture that looks for
different set of selector tags or uses a different predicate to determine
which things to block:

```clj
(defn deny? [permission]
  (instance? java.net.SocketPermission permission))

(use-fixtures :each (isolation/enforce [:io :network] deny?))
```

In this example we only block tests which use `SocketPermission`. The `deny?`
predicate will be passed an instance of [java.security.Permission](https://docs.oracle.com/javase/8/docs/api/java/security/Permission.html)
and must return a boolean indicating whether that permission should be denied.
In this case only `deftest` forms with `^:io` or `^:network` selector tags
will not have that restriction applied.

This feature sets the JVM's [SecurityManager](https://docs.oracle.com/javase/8/docs/api/java/lang/SecurityManager.html)
for the duration of each test, and will not work when testing code that sets or
otherwise assumes it has control of the system `SecurityManager`.

## Differences from `clojure.test`
* supports more than one test reporter at a time
* includes elapsed time measured with `System/nanoTime` when reporting the end
  of a `deftest`
* test fixtures are run when testing a single test, not just a set of tests
* `:once` fixtures are run exactly once per invocation, whether testing an
  entire namespace or a single test
* test selectors are a part of the library, not a monkeypatch
  

## Caveats
* Invoking a test-fn directly will not run any fixtures at all, exactly like
  `clojure.test`. This is due to behaviour from `clojure.test/deftest` and
  can't be worked around without forcing use of a different `deftest`
  implementation.

## Code Coverage support

You can get code coverage support while using this library by using
version 1.0.10 or higher of [Cloverage](https://github.com/cloverage/cloverage)
specifying `circleci.test` as your test runner, like so:

```
lein cloverage --runner circleci.test
```

## Design goals

#### Compatibility with tests written using `clojure.test`

Don't force people to re-write tests just to use a different test runner.

Maintain compatibility with the `clojure.test` assertion expansions such as
[slingshot.test](https://github.com/scgilardi/slingshot/blob/release/src/slingshot/test.clj)

#### Extensible test reporting

Must be possible to use more than one test reporter at a time, for instance it's
desirable to produce Junit XML and console output during a test run.

## License

Copyright © 2017-2020 Circle Internet Services and contributors

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
