# Changes

## 0.5.0: 2020-10-04

* Add support for CIDER test reporting.

## 0.4.2: 2019-02-22

* Supply a default test selector if it is missing from config.

## 0.4.1: 2018-01-17

* Suppress noisy generative testing debug output.

## 0.4.0: 2017-11-02

* Add support for Cloverage; use `--runner circleci.test`.
* Report on Exceptions thrown from fixtures.
* Skip running a namespace's fixtures when no tests are run.

## 0.3.1: 2017-07-14

* Fix a bug where unknown selectors would be silently ignored.
* Fix a bug where retest would not retest errors, only failures.

## 0.3.0: 2017-06-21

* Add retest support.
* Allow JUnit test reporter to be configured.
* Better consistency for when to apply selectors.
* Add global fixtures.

## 0.2.0: 2017-05-08

* Initial public release.
