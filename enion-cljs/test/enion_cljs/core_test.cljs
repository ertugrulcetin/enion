(ns enion-cljs.core-test
  (:require
    [cljs.test :refer-macros [deftest testing is]]))

(deftest fake-test
  (testing "fake description"
    (is (= 1 2))))
