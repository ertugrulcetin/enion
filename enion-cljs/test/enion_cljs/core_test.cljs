(ns enion-cljs.core-test
  (:require
    [cljs.test :refer-macros [deftest testing is]]
    [enion-cljs.core :as core]))

(deftest fake-test
  (testing "fake description"
    (is (= 1 2))))
