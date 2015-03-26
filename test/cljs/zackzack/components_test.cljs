(ns zackzack.components-test
  (:require-macros [cemerick.cljs.test :as m
                    :refer (deftest testing are is)])
  (:require [cemerick.cljs.test :as t]
            [zackzack.components :as z]))

;; lein test doesn't work, perhaps lein-cljsbuild or com.cemerick/clojurescript.test is somehow broken

(deftest unit-test
  (is (= 1 1)))
