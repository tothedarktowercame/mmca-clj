(ns mmca.rng-test
  (:require [clojure.test :refer [deftest is testing]]
            [mmca.rng :as rng]))

(deftest emacs-seed-folding-and-stream
  (testing "the pure port matches GNU Emacs 30 on Linux"
    (is (= 1886339677 (rng/string-seed "prop-0")))
    (let [r (rng/make-rng "prop-0")]
      (is (= [245 78 238 9 143 161 104 203 182 242
              164 105 124 186 73 87 72 254 13 89]
             (mapv (fn [_] (rng/rand-int r 256)) (range 20)))))))
