(ns mmca.hinton-nowlan
  "A deterministic planted-target positive control for the evolutionary apparatus.

   This is not evidence about the MetaCA substrate. It answers a narrower gate:
   can the same truncation/elitism/mutation shape assimilate when a known smooth
   Baldwin path has deliberately been supplied?")

(def target
  [1 0 1 1 0 0 1 0 1 0 0 1 1 1 0 1 0 0 1 0])

(defn compatible? [genome]
  (every? true?
          (map (fn [allele expected]
                 (or (= :? allele) (= expected allele)))
               genome target)))

(defn plastic-count [genome]
  (count (filter #{:?} genome)))

(defn learned-function [genome]
  (if (compatible? genome) 1.0 0.0))

(defn expected-learning-score
  "Exact success probability for uniformly guessing every plastic locus."
  [genome]
  (if (compatible? genome)
    (Math/pow 2.0 (- (plastic-count genome)))
    0.0))

(defn mutate-genome [^java.util.Random rng genome rate]
  (mapv (fn [allele]
          (if (< (.nextDouble rng) rate)
            (nth [:? 0 1] (.nextInt rng 3))
            allele))
        genome))

(defn run
  [{:keys [generations population-size mutation-rate evolution-seed]
    :or {generations 100 population-size 200 mutation-rate 0.02
         evolution-seed 20260730}}]
  (let [rng (java.util.Random. (long evolution-seed))
        next-id (atom 0)
        fresh-id #(swap! next-id inc)
        initial (vec
                 (repeatedly population-size
                             #(hash-map :id (fresh-id)
                                        :field (vec (repeat (count target) :?)))))]
    (loop [generation 0 population initial trajectory []]
      (if (>= generation generations)
        trajectory
        (let [scored (mapv #(assoc % :score (expected-learning-score (:field %)))
                           population)
              ranked (vec (sort-by :score > scored))
              best (first ranked)
              n (count ranked)
              survivors (vec (take (max 1 (quot n 2)) ranked))
              offspring
              (vec
               (repeatedly
                (- n (count survivors))
                #(let [parent (nth survivors (.nextInt rng (count survivors)))]
                   {:id (fresh-id)
                    :parent (:id parent)
                    :field (mutate-genome rng (:field parent) mutation-rate)})))
              row {:generation generation
                   :best-score (:score best)
                   :best-plastic (plastic-count (:field best))
                   :best-function (learned-function (:field best))
                   :mean-plastic
                   (/ (reduce + (map #(plastic-count (:field %)) scored))
                      (double (* n (count target))))}]
          (recur (inc generation)
                 (into (mapv #(select-keys % [:id :field]) survivors) offspring)
                 (conj trajectory row)))))))

(defn positive-control-passes? [trajectory]
  (and (every? #(= 1.0 (:best-function %)) trajectory)
       (zero? (:best-plastic (last trajectory)))
       (< (:best-plastic (last trajectory))
          (:best-plastic (first trajectory)))))
