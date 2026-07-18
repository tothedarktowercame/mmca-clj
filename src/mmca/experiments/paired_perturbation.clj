(ns mmca.experiments.paired-perturbation
  "Excursion E1 of M-metaca-eoc: same-randomness paired-perturbation response.

  Fork a Tier-1 replay at time t*, intervene in ONE branch, and measure the
  four-channel response matrix R_{B<-A}(dt) for A,B in {G,X}. A propagator write
  draws one genotype-INDEPENDENT position per cell, so both branches consume the
  identical RNG tape; any divergence is the causal effect of the intervention
  (Codex's same-randomness requirement, free here).

  Base run-propagator is feedforward: X->G is 0 by construction (a correctness
  check). The live/dead lambda=1/2 pair (Rule 105 live, Rule 204 dead identity)
  is the neighbourhood-semantics scalpel.

  Deterministic: same seeds/width/steps/t* => identical output."
  (:require [mmca.core :as c] [mmca.rng :as rng]))

(defn clone-rng [r] (rng/->EmacsRng (atom @(:state r)) (atom @(:index r))))

(defn- step-base [r g p wr]
  ;; one feedforward step from (g,p): phenotype (no RNG), then genotype (RNG)
  (let [np (c/phenotype-step g p)
        ng (c/genotype-step r g wr true)]
    [ng np]))

;; --- interventions: (fn [g p x] -> [g' p']) at site x ------------------------
(defn flip-phenotype-bit [g p x]
  [g (str (subs p 0 x) (if (= \1 (nth p x)) \0 \1) (subs p (inc x)))])
(defn flip-rule-bit [g p x]
  [(assoc g x (bit-xor (nth g x) 1)) p])           ; flip LSB of the site's rule
(defn swap-rule [rule] (fn [g p x] [(assoc g x rule) p]))

(defn diverge
  "Run to t*, fork, perturb branch B at site x, continue both to `steps`.
   Returns a vector of {:dt :dG :dX} = affected mass per layer over time."
  [writing seed width steps t* x intervention]
  (let [wr (c/positional-writing->neighbourhood-writing writing)
        r  (rng/make-rng (format "prop-%d" seed))
        g0 (c/random-genotype r width)
        p0 (c/random-phenotype r width)]
    (loop [t 0 g g0 p p0]
      (if (= t t*)
        (let [rA (clone-rng r) rB (clone-rng r)
              [gB0 pB0] (intervention g p x)]
          (loop [tt t* gA g pA p gB gB0 pB pB0 acc []]
            (if (= tt steps)
              acc
              (let [[gA' pA'] (step-base rA gA pA wr)
                    [gB' pB'] (step-base rB gB pB wr)]
                (recur (inc tt) gA' pA' gB' pB'
                       (conj acc {:dt (- (inc tt) t*)
                                  :dG (c/changed-count gA' gB')
                                  :dX (count (remove true? (map = pA' pB')))}))))))
        (let [[g' p'] (step-base r g p wr)]
          (recur (inc t) g' p'))))))

(defn channel-matrix
  "Aggregate mean affected mass at chosen dt over seeds, for each intervention.
   Returns the 4 channels: X->X, X->G (from an X-flip) and G->G, G->X (rule-flip)."
  [writing seeds width steps t* dts]
  (let [x (quot width 2)
        agg (fn [intervention]
              (let [runs (map #(diverge writing % width steps t* x intervention) seeds)]
                (into {} (for [d dts]
                           [d (let [row (map #(nth % (dec d)) runs)]
                                {:G (/ (reduce + (map :dG row)) (double (count row)))
                                 :X (/ (reduce + (map :dX row)) (double (count row)))})]))))]
    {:X (agg flip-phenotype-bit)     ; response to a phenotype-bit flip
     :G (agg flip-rule-bit)}))       ; response to a rule-bit flip

(defn -main [& _]
  (let [writing [4 5 6 7 0 1 2 3]   ; offset+4 (all-even, gcd 4) -- collapses
        seeds (range 0 40) width 80 steps 120 t* 40 dts [1 5 20 60]]
    (println "E1 paired-perturbation | offset+4 | feedforward base | seeds 0-39 W80 T120 t*40")
    (let [m (channel-matrix writing seeds width steps t* dts)]
      (println "\n  intervention = flip one PHENOTYPE bit at centre:")
      (doseq [d dts] (let [{:keys [G X]} (get-in m [:X d])]
                       (println (format "    dt=%-3d  X->X mass=%.2f   X->G mass=%.2f  (must be 0 on base)" d X G))))
      (println "\n  intervention = flip one RULE bit at centre:")
      (doseq [d dts] (let [{:keys [G X]} (get-in m [:G d])]
                       (println (format "    dt=%-3d  G->G mass=%.2f   G->X mass=%.2f" d G X))))
      (let [xg (reduce + (for [d dts] (get-in m [:X d :G])))]
        (println (format "\n  CHECK: total X->G across dt = %.4f  => %s"
                         xg (if (zero? xg) "PASS (feedforward null holds)" "FAIL")))))
    ;; live/dead lambda=1/2 scalpel: differential G->X from injecting 105 vs 204
    (println "\n  scalpel: inject Rule 105 (live) vs Rule 204 (dead) at centre, G->X mass:")
    (doseq [[lbl rule] [["105 live" 105] ["204 dead" 204]]]
      (let [x (quot width 2)
            runs (map #(diverge writing % width steps t* x (swap-rule rule)) seeds)
            at (fn [d] (/ (reduce + (map #(:dX (nth % (dec d))) runs)) (double (count runs))))]
        (println (format "    %-9s  dt=5 %.2f   dt=20 %.2f   dt=60 %.2f" lbl (at 5) (at 20) (at 60)))))))
