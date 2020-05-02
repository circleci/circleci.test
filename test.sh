grep defproject project.clj | cut -d" " -f3 | xargs -I {} echo "(def circleci-test-version \"{}\")" > dev-resources/weasley/circleci.test.version

lein install

cd dev-resources/weasley/

lein do clean, compile

# Counts between leiningen and circleci are off because of tests with global fixtures (ns3)
lein test > test_out.txt &&
grep "Ran 33 tests containing 108 assertions." test_out.txt &&
lein update-in :aliases empty -- update-in : assoc :test-selectors '{:default (complement :global-fixture)}'  -- test > test_out.txt &&
grep "Ran 18 tests containing 60 assertions." test_out.txt &&

lein tests weasley.sample-test-ns1 weasley.sample-test-ns2 > test_out.txt &&
grep "Ran 18 tests containing 60 assertions." test_out.txt &&
lein update-in :aliases empty -- test :only weasley.sample-test-ns1 weasley.sample-test-ns2 > test_out.txt &&
grep "Ran 18 tests containing 60 assertions." test_out.txt &&

lein tests weasley.sample-test-ns1 > test_out.txt &&
grep "Ran 3 tests containing 12 assertions." test_out.txt &&
lein update-in :aliases empty -- test :only weasley.sample-test-ns1 > test_out.txt &&
grep "Ran 3 tests containing 12 assertions." test_out.txt &&

lein tests weasley.sample-test-ns2 > test_out.txt &&
grep "Ran 15 tests containing 48 assertions." test_out.txt &&
lein update-in :aliases empty -- test :only weasley.sample-test-ns2 > test_out.txt &&
grep "Ran 15 tests containing 48 assertions." test_out.txt &&

lein tests weasley.sample-test-ns2 weasley.sample-test-ns2 weasley.sample-test-ns2 weasley.sample-test-ns2 > test_out.txt &&
grep "Ran 15 tests containing 48 assertions." test_out.txt &&
lein update-in :aliases empty -- test :only weasley.sample-test-ns2 weasley.sample-test-ns2 weasley.sample-test-ns2 > test_out.txt &&
grep "Ran 15 tests containing 48 assertions." test_out.txt &&

lein tests x y/z > test_out.txt &&
grep "Ran 0 tests containing 0 assertions." test_out.txt &&
lein update-in :aliases empty -- test :only x y/z > test_out.txt &&
grep "Ran 0 tests containing 0 assertions." test_out.txt &&

lein tests weasley.sample-test-ns2 weasley.sample-test-ns1/test-1 x y/z > test_out.txt &&
grep "Ran 16 tests containing 52 assertions." test_out.txt &&
lein update-in :aliases empty -- test :only weasley.sample-test-ns2 weasley.sample-test-ns1/test-1 x y/z > test_out.txt &&
grep "Ran 16 tests containing 52 assertions." test_out.txt &&

lein tests weasley.sample-test-ns2/test-1 > test_out.txt &&
grep "Ran 1 tests containing 4 assertions." test_out.txt &&
lein update-in :aliases empty -- test :only weasley.sample-test-ns2/test-1 > test_out.txt &&
grep "Ran 1 tests containing 4 assertions." test_out.txt &&

lein tests weasley.sample-test-ns2/test-1 weasley.sample-test-ns2/test-2 > test_out.txt &&
grep "Ran 2 tests containing 8 assertions." test_out.txt &&
lein update-in :aliases empty -- test :only weasley.sample-test-ns2/test-1 weasley.sample-test-ns2/test-2 > test_out.txt &&
grep "Ran 2 tests containing 8 assertions." test_out.txt &&

lein tests weasley.sample-test-ns2/test-1 weasley.sample-test-ns2 weasley.sample-test-ns2/test-2 weasley.sample-test-ns2/test-1 > test_out.txt &&
grep "Ran 15 tests containing 48 assertions." test_out.txt &&
lein update-in :aliases empty -- test :only weasley.sample-test-ns2/test-1 weasley.sample-test-ns2 weasley.sample-test-ns2/test-2 weasley.sample-test-ns2/test-1 > test_out.txt &&
grep "Ran 15 tests containing 48 assertions." test_out.txt &&

lein tests :select-vars weasley.sample-test-ns2 > test_out.txt &&
grep "Ran 1 tests containing 4 assertions." test_out.txt &&
lein update-in :aliases empty -- test :only weasley.sample-test-ns2/test-2 > test_out.txt &&
grep "Ran 1 tests containing 4 assertions." test_out.txt &&

lein tests :select-vars weasley.sample-test-ns1 > test_out.txt &&
grep "Ran 1 tests containing 4 assertions." test_out.txt &&
lein update-in :aliases empty -- test :only weasley.sample-test-ns1/test-2 > test_out.txt &&
grep "Ran 1 tests containing 4 assertions." test_out.txt &&

# The equivalent run for this in Leiningen does not work (test-ns-hook)
lein tests :combination weasley.sample-test-ns2 > test_out.txt &&
grep "Ran 12 tests containing 36 assertions." test_out.txt &&

# The equivalent run for this in Leiningen does not work (test calling another test)
lein tests :combination weasley.sample-test-ns2/test-4 weasley.sample-test-ns2/test-5 weasley.sample-test-ns2/test-1 > test_out.txt &&
grep "Ran 12 tests containing 36 assertions." test_out.txt &&

lein tests :combination weasley.sample-test-ns1 > test_out.txt &&
grep "Ran 0 tests containing 0 assertions." test_out.txt &&
lein update-in :aliases empty -- update-in : assoc :test-selectors '{:combination :combination}' -- test weasley.sample-test-ns1 :combination > test_out.txt &&
grep "Ran 0 tests containing 0 assertions." test_out.txt &&

# The equivalent run for this in Leiningen does not work (test calling another test)
lein tests :combination weasley.sample-test-ns2/test-4 > test_out.txt &&
grep "Ran 4 tests containing 12 assertions." test_out.txt &&

# Global fixtures tests
lein tests weasley.sample-test-ns3 > test_out.txt &&
grep "Ran 15 tests containing 48 assertions." test_out.txt &&

lein tests weasley.sample-test-ns3/test-1 > test_out.txt &&
grep "Ran 1 tests containing 4 assertions." test_out.txt &&

lein tests weasley.sample-test-ns3/test-1 weasley.sample-test-ns3/test-2 > test_out.txt &&
grep "Ran 2 tests containing 8 assertions." test_out.txt &&

lein tests :select-vars weasley.sample-test-ns3/test-1 weasley.sample-test-ns3/test-2 > test_out.txt &&
grep "Ran 1 tests containing 4 assertions." test_out.txt &&

lein tests :combination weasley.sample-test-ns3 > test_out.txt &&
grep "Ran 12 tests containing 36 assertions." test_out.txt &&

rm -f test_out.txt
