(ns scripts.river-perturbation-rows
  (:require [mmca.core :as c]))
(defn- runner [mode] (if (= mode :river) c/run-river-from c/run-river-ablated-from))
(defn two-stage [mode seed width steps t* intervene]
  (let [f (runner mode) r (java.util.Random. (long seed))
        g0 (c/java-random-genotype r width) p0 (c/java-random-phenotype r width)
        a (f r g0 p0 t*) g* (peek (:gen a)) p* (peek (:phe a))
        [g' p'] (if intervene (intervene g* p*) [g* p*])
        b (f r g' p' (- steps t*))]
    {:phe (into (:phe a) (rest (:phe b)))}))
(defn flip-at [x] (fn [g p] [g (str (subs p 0 x) (if (= \1 (nth p x)) \0 \1) (subs p (inc x)))]))
(let [width 80 steps 120 t* 60 out (StringBuilder.)]
  (doseq [seed (range 4) mode [:river :ablated]]
    (let [A (two-stage mode seed width steps t* nil)]
      (doseq [x (range 0 width 8)]
        (let [B (two-stage mode seed width steps t* (flip-at x))]
          (doseq [dt (range 1 60)]
            (let [row (map #(if (= %1 %2) \0 \1)
                           (nth (:phe A) (+ t* dt)) (nth (:phe B) (+ t* dt)))]
              (.append out (format "%d\t%s\t%d\t%d\t%s\n" seed (name mode) x dt (apply str row)))))))))
  (spit "data/pert_rows.tsv" (str out)) (println "wrote data/pert_rows.tsv"))
