(ns validate-baldwin-guidance-arm
  (:require [clojure.edn :as edn]))

(let [[path budget neutral generations population seed field-rate output]
      *command-line-args*
      manifest (edn/read-string (slurp path))
      observed (:configuration manifest)
      expected {:mode "guidance-field" :cost 0.0
                :generations (Long/parseLong generations)
                :population (Long/parseLong population)
                :evaluation-seed-count 3 :evaluation-site-count 4
                :field-rate (Double/parseDouble field-rate) :warmup 0
                :hgt false :neutral (= neutral "1")
                :learning-budget (Long/parseLong budget) :seed-offset 0
                :mutation-mode nil :p0-mode :variable :fixed-p0 nil
                :evolution-seed (Long/parseLong seed)
                :pin nil :gamma-pinned true :plasticity-pinned true
                :hold-pinned true}
      passed? (and (= expected observed)
                   (= [1 2 3] (:evaluation-seeds manifest))
                   (= [0 20 40 60] (:evaluation-sites manifest)))]
  (spit output
        (str (pr-str {:kind :guidance-arm-configuration
                      :passed? passed? :expected expected :observed observed}) "\n"))
  (when-not passed? (System/exit 1)))
