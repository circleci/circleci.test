# circleci.test

A Clojure test-runner compatible with tests written using `clojure.test`

## Usage

Add `[circleci/circleci.test "0.1.37"]` to your dev dependencies.

Run tests with `lein run -m circleci.test my.first.test.ns my.second.test.ns`

### Leiningen plugin
The plugin `circleci/lein-circleci-test` provides a Leiningen task for running
tests. It respects the `:test-selectors` defined in your project.clj

Add `[circleci/lein-circleci-test "0.1.0"]` to your `:plugins`

Run tests with `lein circleci-test`

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


## Differences from `clojure.test`
* supports more than one test reporter at a time
* includes elapsed time measured with `System/nanoTime` when reporting the end
  of a `deftest`
* test fixtures are run when testing a single test, not just a set of tests
* `:once` fixtures are run exactly once per invocation, whether testing an
  entire namespace or a single test
  

## Caveats
* Invoking a test-fn directly will not run any fixtures at all, exactly like
  `clojure.test`. This is due to behaviour from `clojure.test/deftest` and
  can't be worked around without forcing use of a different `deftest`
  implementation.


## Design goals

#### Compatibility with tests written using `clojure.test`
Don't force people to re-write tests just to use a different test runner.

Maintain compatibility with the `clojure.test` assertion expansions such as [slingshot.test](https://github.com/scgilardi/slingshot/blob/release/src/slingshot/test.clj)

#### Extensible test reporting
Must be possible to use more than one test reporter at a time, for instance it's desirable to produce Junit XML and console output during a test run.


## License

Copyright © 2017 Circle Internet Services

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
