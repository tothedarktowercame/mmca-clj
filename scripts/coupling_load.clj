;; How much work is the phenotype read actually doing?
;;
;; At every cell and step the river's genotype update computes
;;   (original-river-combine-rule pred centre succ context)
;; where `context` is four phenotype bits. Ask, per (cell, step): does the LIVE
;; context produce a different rule than a FROZEN context would? Than no context
;; at all? That fraction is the coupling load -- how often the phenotype read
;; changes the answer rather than merely being consulted.
;;
;; This bears on assimilability. If the read changes the output rarely, a blind
;; rule could approximate the river by getting the common case right. If it
;; changes it often, the information is dense and no blind rule can stand in.
(require '[mmca.core :as c])

(defn load-at [seed width steps]
  (let [r (java.util.Random. (long seed))
        g0 (c/java-random-genotype r width)
        p0 (c/java-random-phenotype r width)
        frozen p0]
    (loop [t 0 g g0 p p0 diff-frozen 0 diff-nil 0 tot 0]
      (if (= t steps)
        [diff-frozen diff-nil tot]
        (let [np (c/phenotype-step g p)
              ctx (fn [ph nph i]
                    (when (and (pos? i) (< i (dec width)))
                      [(Character/digit (nth ph (dec i)) 2)
                       (Character/digit (nth ph i) 2)
                       (Character/digit (nth ph (inc i)) 2)
                       (Character/digit (nth nph i) 2)]))
              [df dn n]
              (reduce (fn [[df dn n] i]
                        (let [pre (if (zero? i) c/default-rule (nth g (dec i)))
                              ctr (nth g i)
                              suc (if (= i (dec width)) c/default-rule (nth g (inc i)))
                              live (c/original-river-combine-rule pre ctr suc (ctx p np i))
                              froz (c/original-river-combine-rule pre ctr suc (ctx frozen frozen i))
                              none (c/original-river-combine-rule pre ctr suc nil)]
                          [(if (= live froz) df (inc df))
                           (if (= live none) dn (inc dn))
                           (inc n)]))
                      [0 0 0] (range width))
              ng (mapv (fn [i]
                         (let [pre (if (zero? i) c/default-rule (nth g (dec i)))
                               ctr (nth g i)
                               suc (if (= i (dec width)) c/default-rule (nth g (inc i)))]
                           (c/propagate-at
                            (c/original-river-combine-rule pre ctr suc (ctx p np i))
                            c/river-writing (.nextInt r c/bit-count))))
                       (range width))]
          (recur (inc t) ng np (+ diff-frozen df) (+ diff-nil dn) (+ tot n)))))))

(println "seed\tdiff-vs-frozen\tdiff-vs-none\ttotal")
(doseq [s [1 2 3 4]]
  (let [[df dn n] (load-at s 80 120)]
    (println (format "%d\t%d\t%d\t%d" s df dn n))))
