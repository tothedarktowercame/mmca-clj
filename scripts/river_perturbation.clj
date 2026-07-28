(ns scripts.river-perturbation
  (:require [clojure.string :as str]
            [mmca.core :as c]))

(defn- runner [mode] (if (= mode :river) c/run-river-from c/run-river-ablated-from))

(defn two-stage
  "Run the river (or ablated control) to t*, optionally intervene, continue.
   Fork is by re-seeding: java.util.Random is deterministic from its seed and
   the per-step draw count is fixed, so both branches share the exact tape."
  [mode seed width steps t* intervene]
  (let [f (runner mode)
        r (java.util.Random. (long seed))
        g0 (c/java-random-genotype r width)
        p0 (c/java-random-phenotype r width)
        a (f r g0 p0 t*)
        g* (peek (:gen a)) p* (peek (:phe a))
        [g' p'] (if intervene (intervene g* p*) [g* p*])
        b (f r g' p' (- steps t*))]
    {:gen (into (:gen a) (rest (:gen b)))
     :phe (into (:phe a) (rest (:phe b)))}))

(defn flip-at [x] (fn [g p] [g (str (subs p 0 x) (if (= \1 (nth p x)) \0 \1) (subs p (inc x)))]))

(defn damage [A B]
  (mapv (fn [pa pb] (mapv #(if (= %1 %2) 0 1) pa pb)) (:phe A) (:phe B)))

(let [seed 1 width 80 steps 120 t* 60
      out (StringBuilder.)]
  (.append out "mode\tsite\tdt\tmass\tspread\n")
  (doseq [mode [:river :ablated]]
    (let [A (two-stage mode seed width steps t* nil)]
      ;; reference phenotype at t* for band classification
      (when (= mode :river)
        (spit "data/pert_phe.txt" (str/join "\n" (:phe A))))
      (doseq [x (range width)]
        (let [B (two-stage mode seed width steps t* (flip-at x))
              D (damage A B)]
          (when (and (= mode :river) (#{20 40 60} x))
            (spit (format "data/pert_grid_%d.txt" x)
                  (str/join "\n" (map #(str/join " " %) D))))
          (doseq [dt [1 5 10 20 40 59]]
            (let [row (nth D (+ t* dt) nil)]
              (when row
                (let [idx (keep-indexed (fn [i v] (when (= 1 v) i)) row)
                      spread (if (seq idx)
                               (apply max (map #(min (Math/abs (- % x))
                                                     (- width (Math/abs (- % x)))) idx)) 0)]
                  (.append out (format "%s\t%d\t%d\t%d\t%d\n"
                                       (name mode) x dt (reduce + row) spread))))))))))
  (spit "data/pert_summary.tsv" (str out))
  (println "wrote data/pert_summary.tsv, data/pert_phe.txt, 3 grids"))
