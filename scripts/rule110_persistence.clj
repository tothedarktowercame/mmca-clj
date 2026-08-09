;; Rule-110 dominance persistence behind Figure 2 (fig:regimes) of the paper:
;; sigma=16250374 vs foil 10275364, widths 60-480, six seeds, t in {360,600,1000}.
;; Run: clojure -M scripts/rule110_persistence.clj   (from mmca-clj)
(require '[mmca.core :as mmca])
(def w110 (mmca/positional-writing->neighbourhood-writing [1 6 2 5 0 3 7 4]))
(def wfoil (mmca/positional-writing->neighbourhood-writing [1 0 2 7 5 3 6 4]))
(defn frac [row s] (double (/ (count (filter s row)) (count row))))
(doseq [[nm w] [["16250374" w110] ["10275364" wfoil]]
        L [60 120 240 480]]
  (let [per-seed (for [seed [1 2 3 4 5 6]]
                   (let [gen (:gen (mmca/run-propagator w seed L 1000))]
                     (mapv (fn [t] (frac (nth gen t) #{110})) [360 600 1000])))]
    (doseq [[i t] (map-indexed vector [360 600 1000])]
      (let [vs (sort (map #(nth % i) per-seed))]
        (println (format "%s L=%3d t=%4d rule110 median=%.3f min=%.3f max=%.3f"
                         nm L t (nth vs 2) (first vs) (last vs)))))))
