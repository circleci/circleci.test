weasley-test: root-test
	@echo "------------------------------------"
	@echo "Testing internal project Weasley"
	@echo "------------------------------------"

	# The goal is to compare test runs with Leiningen where
	# possible (is there a better way than this?)
	sh test.sh

root-test:
	@echo "------------------------------------"
	@echo "Testing root project circleci.test"
	@echo "------------------------------------"
	lein do clean, compile
	lein test

test: root-test

.PHONY: test
