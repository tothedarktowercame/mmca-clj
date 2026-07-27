(require '[mmca.core :as c] '[clojure.string :as str])
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
  (.append out "seed\tmode\tsite\tmass\tspread\n")
  (doseq [seed (range 6) mode [:river :ablated]]
    (let [A (two-stage mode seed width steps t* nil)]
      (doseq [x (range 0 width 4)]
        (let [B (two-stage mode seed width steps t* (flip-at x))
              row (mapv #(if (= %1 %2) 0 1) (nth (:phe A) (+ t* 59)) (nth (:phe B) (+ t* 59)))
              idx (keep-indexed (fn [i v] (when (= 1 v) i)) row)
              spread (if (seq idx) (apply max (map #(min (Math/abs (- % x)) (- width (Math/abs (- % x)))) idx)) 0)]
          (.append out (format "%d\t%s\t%d\t%d\t%d\n" seed (name mode) x (reduce + row) spread))))))
  (spit "/tmp/pert_seeds.tsv" (str out)) (println "done"))
