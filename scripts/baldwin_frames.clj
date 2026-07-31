;; Dump spacetime frames for one genome, so a run can be inspected by eye.
;;
;; mmca.baldwin-spec decides whether a trajectory certifies a claim; it cannot
;; tell you the dynamics look wrong. These frames are for the sanity check the
;; formalism misses: genotype, phenotype, and -- the one that matters -- the
;; DAMAGE field showing where the perturbed and unperturbed branches differ.
;; Reach is the count of that field's row at dt; if the field does not look like
;; spreading damage, the number is meaningless however exactly it reproduces.

(ns baldwin-frames
  (:require [mmca.baldwin-selection :as bs]
            [mmca.core :as c]))

(def W 80) (def TSTAR 60) (def STEPS 120)

(defn run-pair
  "Evolve to t*, fork with one flipped phenotype bit, continue both branches on
   cloned RNG state, and record every step of genotype, phenotype and A-vs-B
   difference. The frozen reference is supplied from outside and shared, exactly
   as the published ablation requires."
  [{:keys [gamma update-prob field mask hold]} seed site]
  (let [mk (or mask (vec (repeat W true)))
        hd (or hold (vec (repeat W false)))
        up (if (nil? update-prob) 1.0 update-prob)
        mkrng #(java.util.Random. (long %))
        r (mkrng seed) gate (mkrng (+ 987654321 seed)) upd (mkrng (+ 123456789 seed))
        _ (c/java-random-genotype r W)
        p0 (c/java-random-phenotype r W)
        step (fn [rr gg uu g p frozen]
               (let [np (c/phenotype-step g p)]
                 [(bs/gain-genotype-step rr gg uu g p np frozen gamma up mk hd) np]))
        ;; warm-up, shared by both branches
        warm (loop [t 0 g field p p0 acc []]
               (if (= t TSTAR) {:g g :p p :acc acc}
                 (let [[ng np] (step r gate upd g p p0)]
                   (recur (inc t) ng np (conj acc [t g p nil])))))
        gA (:g warm) pA (:p warm)
        pB (apply str (assoc (vec pA) site (if (= \1 (nth pA site)) \0 \1)))
        rA (mkrng seed) rB (mkrng seed)
        gtA (mkrng (+ 987654321 seed)) gtB (mkrng (+ 987654321 seed))
        uA (mkrng (+ 123456789 seed)) uB (mkrng (+ 123456789 seed))]
    ;; advance the forked streams to where the warm-up left them
    (dotimes [_ TSTAR]
      (dotimes [_ W] (.nextDouble ^java.util.Random gtA) (.nextDouble ^java.util.Random gtB)
                     (.nextDouble ^java.util.Random uA) (.nextDouble ^java.util.Random uB)
                     (.nextInt ^java.util.Random rA c/bit-count) (.nextInt ^java.util.Random rB c/bit-count)))
    (into (:acc warm)
          (loop [t TSTAR ga gA pa pA gb gA pb pB acc []]
            (if (= t STEPS) acc
              (let [[nga npa] (step rA gtA uA ga pa p0)
                    [ngb npb] (step rB gtB uB gb pb p0)]
                (recur (inc t) nga npa ngb npb
                       (conj acc [t nga npa (mapv #(if (= %1 %2) 0 1) npa npb)]))))))))

;; With a record file argument, render the BEST EVOLVED genome from that run --
;; what selection actually built, rather than a random draw. Without one, fall back
;; to a random genome.
(let [args *command-line-args*
      genome
      (if-let [rec (first args)]
        (bs/best-genome-from-record rec)
        {:gamma 1.0 :update-prob 1.0
         :field (c/java-random-genotype (java.util.Random. 1) W)
         :mask (vec (repeat W true)) :hold (vec (repeat W false))})
      rows (run-pair genome 1 40)]
  (binding [*out* *err*]
    (println (format "  genome: gamma=%.3f update-prob=%.2f held=%d/%d"
                     (double (:gamma genome)) (double (:update-prob genome))
                     (count (filter true? (:hold genome))) W)))
  (println "kind\tt\ti\tv")
  (doseq [[t g p d] rows i (range W)]
    (println (format "geno\t%d\t%d\t%d" t i (nth g i)))
    (println (format "pheno\t%d\t%d\t%d" t i (Character/digit (nth p i) 2)))
    (when d (println (format "damage\t%d\t%d\t%d" t i (nth d i))))))
