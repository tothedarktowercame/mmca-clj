;; Where does each construction sit on the elementary-rule damage-spreading
;; scale? One protocol throughout -- L=80, t*=60, T=120, flip one PHENOTYPE bit,
;; count differing phenotype cells at dt=59 -- so every number is directly
;; comparable with rules 0 / 204 / 90 / 54 / 110 / 30 and with find:ladder.
;;
;; Mechanism semantics follow diversity_dial2.clj exactly:
;;   blend-strength b : with probability b propagate from the neighbour blend,
;;                      otherwise from the centre rule
;;   async-refuges u  : with probability u propagate from the blend, otherwise
;;                      leave the cell's rule unchanged (u is an UPDATE fraction)
;;   niches           : blend-propagate with the writing selected by patch
;; Conservative transport follows diversity_dial4.clj: Margolus-style disjoint
;; phenotype-gated bijective swaps, which never rewrite a rule.
;; The exotype rows port futon5/scripts/exotype_by_example.clj exactly at the
;; operator level: switch(boring?, rotate+2 propagator, no-op). "Boring" means
;; the circular phenotype neighbourhood [prev self next] is uniform.
(require '[mmca.core :as c] '[mmca.rng :as rng])
(defn clone [r] (rng/->EmacsRng (atom @(:state r)) (atom @(:index r))))
(def W 80) (def TSTAR 60) (def DT 59)
(def exotype-explore-writing
  (c/positional-writing->neighbourhood-writing [2 3 4 5 6 7 0 1]))
(defn agreement-gate?
  "True when at least k cells in the centred circular m-cell neighbourhood
  (including the centre) agree with the centre phenotype bit. Odd m keeps the
  neighbourhood symmetric; m=3, k=3 is the original boring? predicate."
  [phenotype i m k]
  {:pre [(odd? m) (<= 1 k m)]}
  (let [width (count phenotype)
        radius (quot m 2)
        centre (nth phenotype i)]
    (<= k
        (count
          (filter #(= centre (nth phenotype (mod % width)))
                  (range (- i radius) (inc (+ i radius))))))))
(defn boring? [phenotype i]
  (agreement-gate? phenotype i 3 3))
(defn exotype-step [pr genotype phenotype policy activity]
  (let [fires (volatile! 0)
        result
        (mapv (fn [i rule]
                (let [fire? (case policy
                              :explore true
                              :hold false
                              :exotype (boring? phenotype i))]
                  (when fire? (vswap! fires inc))
                  ;; Draw unconditionally and use it only when firing. Otherwise
                  ;; the two damage branches consume different numbers of draws
                  ;; (the gate reads the phenotype, which the perturbation
                  ;; changes), their tapes desynchronise, and the divergence that
                  ;; follows is RNG artefact rather than causal effect. This is
                  ;; the discipline river_gain.clj states explicitly.
                  (let [source (rng/rand-int pr c/bit-count)]
                    (if fire?
                      (c/propagate-at rule exotype-explore-writing source true)
                      rule))))
              (range (count genotype))
              genotype)]
    (swap! activity (fn [{:keys [fired opportunities]}]
                      {:fired (+ fired @fires)
                       :opportunities (+ opportunities (count genotype))}))
    result))
(defn eca-row [rule p]
  (let [n (count p)]
    (apply str (for [i (range n)]
      (let [l (Character/digit (nth p (mod (dec i) n)) 2)
            ce (Character/digit (nth p i) 2)
            r (Character/digit (nth p (mod (inc i) n)) 2)]
        (bit-and (bit-shift-right rule (+ (* 4 l) (* 2 ce) r)) 1))))))
(defn mech-step [pr mr gr g cfg t phenotype gate-phenotype activity]
  (let [{:keys [kind policy wa wb b u patch m k rate]} cfg]
    (cond
      (= kind :preserve) g
      (= kind :exotype) (exotype-step pr g phenotype policy activity)
      (#{:transport :transport-switch :transport-random-switch
         :transport-frozen-switch} kind)
      (let [start (mod t 2)
            indices (range start (dec W) 2)
            switch? (not= kind :transport)
            fires (volatile! 0)
            result
            (reduce (fn [gg i]
                      (let [j (inc i)
                            li (Character/digit (nth phenotype i) 2)
                            lj (Character/digit (nth phenotype j) 2)
                            pr* (min 1.0 (* u (if (= li lj) 0.5 1.5)))
                            ;; The transport stream advances once at every
                            ;; opportunity, whether or not either gate fires.
                            coin (rng/rand-double mr)
                            ;; Random gate coins live on a separate stream and
                            ;; are likewise drawn once per opportunity. `gr` is
                            ;; cloned identically across damage-fork branches.
                            fire? (case kind
                                    :transport true
                                    :transport-switch
                                    (agreement-gate? phenotype i (or m 3) (or k 3))
                                    :transport-random-switch
                                    (< (rng/rand-double gr) rate)
                                    :transport-frozen-switch
                                    (agreement-gate? gate-phenotype i
                                                     (or m 3) (or k 3)))]
                        (when (and switch? fire?) (vswap! fires inc))
                        (if (and fire?
                                 (< coin (if (:ungated cfg) (min 1.0 u) pr*)))
                          (assoc gg i (nth gg j) j (nth gg i))
                          gg)))
                    g indices)]
        (when switch?
          (swap! activity (fn [{total-fired :fired opportunities :opportunities}]
                            {:fired (+ total-fired @fires)
                             :opportunities (+ opportunities (count indices))})))
        result)
      :else
      (let [ord (concat [0 (dec W)] (range 1 (dec W)))]
        (reduce (fn [out i]
                  (let [pred (if (zero? i) c/default-rule (nth g (dec i)))
                        ctr (nth g i)
                        succ (if (= i (dec W)) c/default-rule (nth g (inc i)))
                        blended (c/blend-rule pred ctr succ)
                        source (rng/rand-int pr 8)
                        wr (cond wb (if patch
                                      (if (even? (quot i patch)) wa wb)
                                      (if (even? t) wa wb))
                                 :else wa)
                        nxt (case kind
                              :baseline (c/propagate-at blended wr source true)
                              :blend (c/propagate-at
                                       (if (< (rng/rand-double mr) b) blended ctr)
                                       wr source true)
                              :async (if (< (rng/rand-double mr) u)
                                       (c/propagate-at blended wr source true) ctr))]
                    (assoc out i nxt)))
                (vec (repeat W nil)) ord)))))
(defn run-mech [cfg seed]
  (let [pr (rng/make-rng (format "prop-%d" seed)) mr (rng/make-rng (format "mech-%d" seed))
        gr (rng/make-rng (format "gate-%d" seed))
        nr (rng/make-rng (format "noise-%d" seed))
        activity (atom {:fired 0 :opportunities 0})
        g0 (c/random-genotype pr W) p0 (c/random-phenotype pr W)
        step (fn [p m gate n g t ph fph]
               (let [g' (mech-step p m gate g cfg t ph fph activity)]
                 (if (pos? (or (:noise cfg) 0)) (c/genotype-noise n g' (:noise cfg)) g')))]
    (loop [t 0 g g0 p p0]
      (if (= t TSTAR)
        (let [damages
              (mapv
                (fn [x]
                  (let [pB (apply str (assoc (vec p) x (if (= \1 (nth p x)) \0 \1)))
                        pA' (clone pr) pB' (clone pr) mA (clone mr) mB (clone mr)
                        gateA (clone gr) gateB (clone gr)
                        nA (clone nr) nB (clone nr)]
                    (loop [tt TSTAR gA g a p gB g b pB]
                      (if (= tt (+ TSTAR DT)) (count (remove true? (map = a b)))
                        (recur (inc tt) (step pA' mA gateA nA gA tt a p) (c/phenotype-step gA a)
                                        (step pB' mB gateB nB gB tt b pB) (c/phenotype-step gB b))))))
                (range 0 W 8))]
          (assoc @activity :damages damages))
        (recur (inc t) (step pr mr gr nr g t p p) (c/phenotype-step g p))))))
(let [PA [3 0 1 2 7 4 5 6] T4 [6 7 0 2 1 4 3 5]
      R1 [1 2 3 4 5 6 7 0] R2 [2 3 4 5 6 7 0 1] R4 [4 5 6 7 0 1 2 3]]
  (println "class\tname\tseed\tdamage\tfired\topportunities")
  (doseq [rule [0 204 90 54 110 30] seed (range 4)]
    (let [r (rng/make-rng (format "prop-%d" seed))
          _ (c/random-genotype r W) p0 (c/random-phenotype r W)]
      (doseq [d (loop [t 0 p p0]
                  (if (= t TSTAR)
                    (for [x (range 0 W 8)]
                      (let [pB (apply str (assoc (vec p) x (if (= \1 (nth p x)) \0 \1)))]
                        (loop [tt TSTAR a p b pB]
                          (if (= tt (+ TSTAR DT)) (count (remove true? (map = a b)))
                            (recur (inc tt) (eca-row rule a) (eca-row rule b))))))
                    (recur (inc t) (eca-row rule p))))]
        (println (format "eca\trule %d\t%d\t%d\t0\t0" rule seed d)))))
  (doseq [[cls nm cfg]
          [["family" "$P_a$ (bare)"          {:kind :baseline :wa PA}]
           ["family" "rot$+1$"               {:kind :baseline :wa R1}]
           ["family" "braid $P_a$/two-4"     {:kind :baseline :wa PA :wb T4}]
           ["family" "braid rot$+2$/rot$+4$" {:kind :baseline :wa R2 :wb R4}]
           ["dial" "blend $0.00$"            {:kind :blend :wa PA :b 0.0}]
           ["dial" "blend $0.35$"            {:kind :blend :wa PA :b 0.35}]
           ["dial" "blend $0.70$"            {:kind :blend :wa PA :b 0.70}]
           ["dial" "blend $1.00$"            {:kind :blend :wa PA :b 1.0}]
           ["dial" "braid + blend $0.70$"    {:kind :blend :wa PA :wb T4 :b 0.70}]
           ["dial" "async update $0.25$"     {:kind :async :wa PA :u 0.25}]
           ["dial" "async update $0.75$"     {:kind :async :wa PA :u 0.75}]
           ["dial" "niches $P_a$/two-4 (16)" {:kind :baseline :wa PA :wb T4 :patch 16}]
           ["dial" "niches $P_a$/two-4 (8)"  {:kind :baseline :wa PA :wb T4 :patch 8}]
           ["dial" "mutation $0.10$"         {:kind :baseline :wa PA :noise 0.10}]
           ["dial" "mutation $0.40$"         {:kind :baseline :wa PA :noise 0.40}]
           ["dial" "transport $0.00$"        {:kind :transport :u 0.0}]
           ["dial" "transport $0.05$"        {:kind :transport :u 0.05}]
           ["dial" "transport $0.10$"        {:kind :transport :u 0.10}]
           ["dial" "transport $0.20$"        {:kind :transport :u 0.20}]
           ["dial" "transport $0.35$"        {:kind :transport :u 0.35}]
           ["dial" "transport $0.50$"        {:kind :transport :u 0.5}]
           ["dial" "transport $0.75$"        {:kind :transport :u 0.75}]
           ["dial" "transport $1.00$"        {:kind :transport :u 1.0}]
           ["dial" "ungated $0.20$"          {:kind :transport :u 0.20 :ungated true}]
           ["dial" "ungated $0.35$"          {:kind :transport :u 0.35 :ungated true}]
           ["dial" "ungated $0.50$"          {:kind :transport :u 0.5 :ungated true}]
           ["dial" "ungated $0.75$"          {:kind :transport :u 0.75 :ungated true}]
           ["dial" "ungated $1.00$"          {:kind :transport :u 1.0 :ungated true}]
           ["dial" "preserving limit"        {:kind :preserve}]
           ["exotype" "explore"               {:kind :exotype :policy :explore}]
           ["exotype" "hold"                  {:kind :exotype :policy :hold}]
           ["exotype" "exotype"               {:kind :exotype :policy :exotype}]
           ["exotype" "switch transport $1.00$/hold"
            {:kind :transport-switch :u 1.0 :m 3 :k 3}]
           ["gain-sweep" "switch agree $4/5$, transport $1.00$/hold"
            {:kind :transport-switch :u 1.0 :m 5 :k 4}]
           ["gain-sweep" "switch agree $3/5$, transport $1.00$/hold"
            {:kind :transport-switch :u 1.0 :m 5 :k 3}]
           ["gain-sweep" "switch agree $5/7$, transport $1.00$/hold"
            {:kind :transport-switch :u 1.0 :m 7 :k 5}]
           ["gain-sweep" "switch agree $4/7$, transport $1.00$/hold"
            {:kind :transport-switch :u 1.0 :m 7 :k 4}]
           ["gain-sweep" "switch agree $2/5$, transport $1.00$/hold"
            {:kind :transport-switch :u 1.0 :m 5 :k 2}]
           ["gain-grid" "switch agree $2/3$, transport $1.00$/hold"
            {:kind :transport-switch :u 1.0 :m 3 :k 2}]
           ["gain-grid" "switch agree $5/5$, transport $1.00$/hold"
            {:kind :transport-switch :u 1.0 :m 5 :k 5}]
           ["gain-grid" "switch agree $6/7$, transport $1.00$/hold"
            {:kind :transport-switch :u 1.0 :m 7 :k 6}]
           ["gain-grid" "switch agree $7/7$, transport $1.00$/hold"
            {:kind :transport-switch :u 1.0 :m 7 :k 7}]
           ["gain-grid" "switch agree $5/9$, transport $1.00$/hold"
            {:kind :transport-switch :u 1.0 :m 9 :k 5}]
           ["gain-grid" "switch agree $7/9$, transport $1.00$/hold"
            {:kind :transport-switch :u 1.0 :m 9 :k 7}]
           ["gain-grid" "switch agree $9/9$, transport $1.00$/hold"
            {:kind :transport-switch :u 1.0 :m 9 :k 9}]
           ["gain-frozen" "switch FROZEN agree $2/3$, transport $1.00$/hold"
            {:kind :transport-frozen-switch :u 1.0 :m 3 :k 2}]
           ["gain-frozen" "switch FROZEN agree $3/5$, transport $1.00$/hold"
            {:kind :transport-frozen-switch :u 1.0 :m 5 :k 3}]
           ["gain-frozen" "switch FROZEN agree $4/7$, transport $1.00$/hold"
            {:kind :transport-frozen-switch :u 1.0 :m 7 :k 4}]
           ["gain-frozen" "switch FROZEN agree $5/9$, transport $1.00$/hold"
            {:kind :transport-frozen-switch :u 1.0 :m 9 :k 5}]
           ["gain-frozen" "switch FROZEN agree $4/5$, transport $1.00$/hold"
            {:kind :transport-frozen-switch :u 1.0 :m 5 :k 4}]
           ["gain-frozen" "switch FROZEN agree $5/7$, transport $1.00$/hold"
            {:kind :transport-frozen-switch :u 1.0 :m 7 :k 5}]
           ["gain-frozen" "switch FROZEN agree $7/9$, transport $1.00$/hold"
            {:kind :transport-frozen-switch :u 1.0 :m 9 :k 7}]
           ["gain-random" "switch random $0.099417$, transport $1.00$/hold"
            {:kind :transport-random-switch :u 1.0 :rate 0.09941697285160236}]
           ["gain-random" "switch random $0.120172$, transport $1.00$/hold"
            {:kind :transport-random-switch :u 1.0 :rate 0.12017248418044499}]
           ["gain-random" "switch random $0.236886$, transport $1.00$/hold"
            {:kind :transport-random-switch :u 1.0 :rate 0.23688635435803226}]
           ["gain-random" "switch random $0.646794$, transport $1.00$/hold"
            {:kind :transport-random-switch :u 1.0 :rate 0.6467939885690958}]
           ["gain-random" "switch random $0.683199$, transport $1.00$/hold"
            {:kind :transport-random-switch :u 1.0 :rate 0.6831993774239641}]
           ["gain-random" "switch random $0.935261$, transport $1.00$/hold"
            {:kind :transport-random-switch :u 1.0 :rate 0.9352610226576853}]]
          seed (range (if (#{"exotype" "gain-sweep" "gain-random" "gain-grid"
                              "gain-frozen"} cls) 16 4))]
    (let [{:keys [damages fired opportunities]} (run-mech cfg seed)]
      (doseq [d damages]
        (println (format "%s\t%s\t%d\t%d\t%d\t%d"
                         cls nm seed d fired opportunities))))))
