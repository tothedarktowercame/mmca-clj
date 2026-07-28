;; A gain dial for the river's phenotype-to-genotype edge.
;;
;; The published river and its ablation differ in exactly one respect: the
;; genotype step reads the LIVE evolving phenotype for its four context bits, or
;; the FROZEN t=0 phenotype. That is an on/off switch. This interpolates it:
;; each cell, each step, reads the live phenotype with probability gamma and the
;; frozen one otherwise, so gamma is the fraction of the genotype's view of the
;; phenotype that is causally current -- the gain of the loop.
;;
;; Two invariants make the dial calibrated rather than merely suggestive:
;;   * gamma = 1 must reproduce the live river exactly, gamma = 0 the ablation;
;;   * `random` is consumed once per cell at every gamma, exactly as the river
;;     consumes it, so all gamma share the river's tape. The gate coins come
;;     from a SEPARATE stream, re-seeded identically in both branches of the
;;     fork, so the gate itself never injects divergence.
;; The frozen field is a real phenotype, so marginal statistics and spatial
;; structure are matched at every gamma; only causal currency varies.
(require '[mmca.core :as c] '[clojure.string :as str])

(defn gain-genotype-step
  [^java.util.Random random ^java.util.Random gate genotype phenotype next-phenotype frozen gamma]
  (let [width (count genotype)]
    (mapv
     (fn [i]
       (let [live? (< (.nextDouble gate) gamma)
             ph (if live? phenotype frozen)
             nph (if live? next-phenotype frozen)
             predecessor (if (zero? i) c/default-rule (nth genotype (dec i)))
             centre (nth genotype i)
             successor (if (= i (dec width)) c/default-rule (nth genotype (inc i)))
             context (when (and (pos? i) (< i (dec width)))
                       [(Character/digit (nth ph (dec i)) 2)
                        (Character/digit (nth ph i) 2)
                        (Character/digit (nth ph (inc i)) 2)
                        (Character/digit (nth nph i) 2)])]
         (c/propagate-at
          (c/original-river-combine-rule predecessor centre successor context)
          c/river-writing
          (.nextInt random c/bit-count))))
     (range width))))

(defn run-gain-from [random gate genotype phenotype steps gamma frozen]
  (loop [t 0 g genotype p phenotype gens [genotype] phes [phenotype]]
    (if (= t steps)
      {:gen gens :phe phes}
      (let [np (c/phenotype-step g p)
            ng (gain-genotype-step random gate g p np frozen gamma)]
        (recur (inc t) ng np (conj gens ng) (conj phes np))))))

(defn two-stage
  "`frozen*` is the reference the genotype reads when the gate says stale. It is
  supplied from OUTSIDE and is the same field in both branches of the fork, so
  the stale path is identical in A and B and cannot itself carry the
  perturbation. (Passing it in is the fix for a subtlety in the published
  ablation, which re-captures the frozen field per call and therefore hands the
  perturbed branch a frozen copy that already contains the flip.)"
  [gamma seed width steps t* intervene frozen*]
  (let [r (java.util.Random. (long seed))
        gate (java.util.Random. (long (+ 987654321 seed)))
        g0 (c/java-random-genotype r width)
        p0 (c/java-random-phenotype r width)
        a (run-gain-from r gate g0 p0 t* gamma (or frozen* p0))
        g* (peek (:gen a)) p* (peek (:phe a))
        [g' p'] (if intervene (intervene g* p*) [g* p*])
        b (run-gain-from r gate g' p' (- steps t*) gamma (or frozen* p0))]
    {:phe (into (:phe a) (rest (:phe b)))}))

(defn flip-at [x] (fn [g p] [g (str (subs p 0 x) (if (= \1 (nth p x)) \0 \1) (subs p (inc x)))]))

(let [width 80 steps 120 t* 60
      gammas [0.0 0.125 0.25 0.375 0.5 0.625 0.75 0.875 1.0]]
  (println "gamma\tseed\tsite\tmass")
  (doseq [gamma gammas seed [1 2 3]]
    ;; reference phenotype at t* from the UNPERTURBED run, shared by both branches
    (let [ref (two-stage 1.0 seed width steps t* nil nil)
          frozen* (nth (:phe ref) t*)
          A (two-stage gamma seed width steps t* nil frozen*)]
      (doseq [x (range width)]
        (let [B (two-stage gamma seed width steps t* (flip-at x) frozen*)
              row (map #(if (= %1 %2) 0 1)
                       (nth (:phe A) (+ t* 59)) (nth (:phe B) (+ t* 59)))]
          (println (format "%.4f\t%d\t%d\t%d" gamma seed x (reduce + row))))))))
