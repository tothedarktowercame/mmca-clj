;; Dump spacetime + damage fields for the Figure 9 / Figure 12 CA insets
;; (2026-08-09).  Four field sets, all L=80, T=120, t*=60, flip at site 40:
;;   river live / river ablated        (Fig 9: the reach contrast as cones)
;;   gate live (m=7,k=4) / gate frozen (Fig 12: same statistics, opposite sign)
;; Outputs: data/inset_{river,ablated,gatelive,gatefrozen}_{phe,dmg}.txt
;; phe = branch-A phenotype rows ("0"/"1" chars); dmg = XOR rows ("0 1 ..").
(require '[clojure.string :as str]
         '[mmca.core :as c] '[mmca.rng :as rng])

;; --- river pair: functions copied from scripts/river_perturbation.clj ---
(defn two-stage [mode seed width steps t* intervene]
  (let [f (if (= mode :river) c/run-river-from c/run-river-ablated-from)
        r (java.util.Random. (long seed))
        g0 (c/java-random-genotype r width)
        p0 (c/java-random-phenotype r width)
        a (f r g0 p0 t*)
        g* (peek (:gen a)) p* (peek (:phe a))
        [g' p'] (if intervene (intervene g* p*) [g* p*])
        b (f r g' p' (- steps t*))]
    {:phe (into (:phe a) (rest (:phe b)))}))

(defn flip-at [x] (fn [g p] [g (str (subs p 0 x) (if (= \1 (nth p x)) \0 \1) (subs p (inc x)))]))

(defn dmg-rows [A B]
  (mapv (fn [pa pb] (str/join " " (map #(if (= %1 %2) 0 1) pa pb))) (:phe A) (:phe B)))

(doseq [[mode tag] [[:river "river"] [:ablated "ablated"]]]
  (let [A (two-stage mode 1 80 120 60 nil)
        B (two-stage mode 1 80 120 60 (flip-at 40))]
    (spit (format "data/inset_%s_phe.txt" tag) (str/join "\n" (:phe A)))
    (spit (format "data/inset_%s_dmg.txt" tag) (str/join "\n" (dmg-rows A B)))
    (println tag "done")))

;; --- gate pair: load regime_placement.clj's defns (cut before its main) ---
(let [src (slurp "scripts/regime_placement.clj")
      cut (str/index-of src "(let [PA [3 0 1 2 7 4 5 6]")]
  (load-string (subs src 0 cut)))

(defn run-gate-fields [cfg seed site]
  (let [pr (rng/make-rng (format "prop-%d" seed)) mr (rng/make-rng (format "mech-%d" seed))
        gr (rng/make-rng (format "gate-%d" seed))
        nr (rng/make-rng (format "noise-%d" seed))
        activity (atom {:fired 0 :opportunities 0})
        g0 (c/random-genotype pr W) p0 (c/random-phenotype pr W)
        step (fn [p m gate n g t ph fph]
               (mech-step p m gate g cfg t ph fph activity))]
    (loop [t 0 g g0 p p0 rows [p0]]
      (if (= t TSTAR)
        (let [pB (apply str (assoc (vec p) site (if (= \1 (nth p site)) \0 \1)))
              pA' (clone pr) pB' (clone pr) mA (clone mr) mB (clone mr)
              gateA (clone gr) gateB (clone gr) nA (clone nr) nB (clone nr)]
          (loop [tt TSTAR gA g a p gB g b pB rowsA rows dmg []]
            (if (= tt (+ TSTAR DT))
              {:phe rowsA :dmg dmg}
              (let [gA' (step pA' mA gateA nA gA tt a p)
                    a' (c/phenotype-step gA' a)
                    gB' (step pB' mB gateB nB gB tt b pB)
                    b' (c/phenotype-step gB' b)]
                (recur (inc tt) gA' a' gB' b'
                       (conj rowsA a')
                       (conj dmg (str/join " " (map #(if (= %1 %2) 0 1) a' b'))))))))
        (let [g' (mech-step pr mr gr g cfg t p p activity)]
          (recur (inc t) g' (c/phenotype-step g' p) (conj rows p)))))))

(doseq [[cfg tag] [[{:kind :transport-switch :u 1.0 :m 7 :k 4} "gatelive"]
                   [{:kind :transport-frozen-switch :u 1.0 :m 7 :k 4} "gatefrozen"]]]
  (let [{:keys [phe dmg]} (run-gate-fields cfg 0 40)]
    (spit (format "data/inset_%s_phe.txt" tag) (str/join "\n" phe))
    (spit (format "data/inset_%s_dmg.txt" tag) (str/join "\n" dmg))
    (println tag "done: damage rows" (count dmg))))
(println "DUMP_DONE")
