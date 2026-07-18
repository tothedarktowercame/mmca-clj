(ns mmca.experiments.transient-scaling
  "Excursion E3 of M-metaca-eoc: deterministic transient-collapse scaling.

  Treats the transient as the observable. For the feedforward offset+4
  propagator across the continuous interrupter coordinate q, measure first passage to
  <=3 distinct rules, censored survival, collapse quantiles, discrete hazards,
  and descriptive width-scaling fits."
  (:require [clojure.string :as str]
            [mmca.core :as c]
            [mmca.rng :as rng]))

(def default-config
  {:writing [4 5 6 7 0 1 2 3]
   :qs [0.1 0.5 1.0]
   :seeds (vec (range 64))
   :widths [30 60 120 240]
   :steps 600
   :collapse-threshold 3
   :checkpoints [10 25 50 100 200 400 600]})

(defn collapse-time
  "First t>=0 with at most `threshold` distinct genotype rules, or nil when
  the run remains above threshold through `steps`. Initialisation consumes the
  phenotype RNG draws even though feedforward G evolution does not read X, so
  this is the exact genotype trajectory of core/run-propagator."
  [writing seed width q steps threshold]
  (let [writing (c/positional-writing->neighbourhood-writing writing)
        r (rng/make-rng (format "prop-%d" seed))
        interrupter-r (when-not (= 1.0 (double q))
                        (rng/make-rng (format "interrupt-%d" seed)))
        g0 (c/random-genotype r width)]
    (c/random-phenotype r width)
    (loop [t 0 genotype g0]
      (cond
        (<= (c/distinct-rules genotype) threshold) t
        (= t steps) nil
        :else (recur (inc t)
                     (if (= 1.0 (double q))
                       (c/genotype-step r genotype writing true)
                       (c/genotype-step-interrupted r interrupter-r genotype
                                                    writing q true)))))))

(defn- quantile-time [times n p]
  (let [rank (long (Math/ceil (* p n)))]
    (when (<= rank (count times))
      (nth times (dec rank)))))

(defn- survival [times n t]
  (/ (count (filter #(or (nil? %) (> % t)) times)) (double n)))

(defn- hazard [times lo hi]
  (let [at-risk (filter #(or (nil? %) (> % lo)) times)
        events (count (filter #(and (some? %) (> % lo) (<= % hi)) at-risk))]
    (if (seq at-risk) (/ events (double (count at-risk))) 0.0)))

(defn summarize-width [q width times checkpoints]
  (let [n (count times)
        collapsed (vec (sort (keep identity times)))]
    {:q q
     :width width
     :n n
     :collapsed (count collapsed)
     :censored (- n (count collapsed))
     :quantiles {:q25 (quantile-time collapsed n 0.25)
                 :q50 (quantile-time collapsed n 0.50)
                 :q75 (quantile-time collapsed n 0.75)}
     :survival (into (sorted-map)
                     (map (fn [t] [t (survival times n t)]))
                     checkpoints)
     :hazard (mapv (fn [[lo hi]]
                     {:from lo :to hi :hazard (hazard times lo hi)})
                   (partition 2 1 (cons 0 checkpoints)))}))

(defn- linear-fit [xs ys]
  (let [n (count xs)
        xbar (/ (reduce + xs) (double n))
        ybar (/ (reduce + ys) (double n))
        sxx (reduce + (map #(let [d (- % xbar)] (* d d)) xs))
        slope (if (zero? sxx) 0.0
                  (/ (reduce + (map (fn [x y] (* (- x xbar) (- y ybar))) xs ys))
                     sxx))
        intercept (- ybar (* slope xbar))
        fitted (map #(+ intercept (* slope %)) xs)
        sse (reduce + (map #(let [d (- %1 %2)] (* d d)) ys fitted))
        sst (reduce + (map #(let [d (- % ybar)] (* d d)) ys))]
    {:intercept intercept
     :slope slope
     :r2 (if (zero? sst) (if (zero? sse) 1.0 0.0) (- 1.0 (/ sse sst)))}))

(defn scaling-fits
  "Descriptive fits over uncensored medians. With only four widths these rank
  hypotheses; they do not establish an asymptotic law."
  [summaries]
  (let [points (keep (fn [{:keys [width quantiles]}]
                       (when-let [median (:q50 quantiles)] [width median]))
                     summaries)
        widths (mapv (comp double first) points)
        medians (mapv (comp double second) points)]
    (when (>= (count points) 3)
      (let [log-widths (mapv #(Math/log %) widths)
            log-medians (mapv #(Math/log %) medians)]
        {:logarithmic (linear-fit log-widths medians)
         :power-law (linear-fit log-widths log-medians)
         :exponential (linear-fit widths log-medians)}))))

(defn run-experiment [{:keys [writing seeds widths steps collapse-threshold
                              checkpoints qs]
                       :as config}]
  (when-let [bad-q (first (remove #(<= 0.0 (double %) 1.0) qs))]
    (throw (ex-info "E3 q must be in [0,1]"
                    {:type :e3/invalid-q :q bad-q})))
  (let [checkpoints (->> checkpoints (filter #(<= % steps)) distinct sort vec)
        by-width (mapv (fn [[q width]]
                         (let [times (mapv #(collapse-time writing % width q steps
                                                            collapse-threshold)
                                           seeds)]
                           (assoc (summarize-width q width times checkpoints)
                                  :times times)))
                       (for [q qs width widths] [q width]))
        public-widths (mapv #(dissoc % :times) by-width)]
    {:config (-> config
                 (assoc :seeds [(first seeds) (last seeds)]
                        :seed-count (count seeds)
                        :checkpoints checkpoints))
     :widths public-widths
     :fits (into (sorted-map)
                 (map (fn [q]
                        [q (scaling-fits (filter #(= q (:q %)) public-widths))]))
                 qs)}))

(defn- fmt [x]
  (if (number? x) (format "%.3f" (double x)) "censored"))

(defn- quantile-cell [x steps]
  (if (some? x) (str x) (str ">" steps)))

(defn render-report [{:keys [config widths fits]}]
  (let [checkpoints (:checkpoints config)
        best-fits (into {} (keep (fn [[q models]]
                                   (when models
                                     [q (key (apply max-key (comp :r2 val) models))])))
                        fits)
        censored-median? (some #(nil? (get-in % [:quantiles :q50])) widths)
        header (str "| q | L | collapsed/n | censored | q25 | median | q75 | "
                    (str/join " | " (map #(str "S(" % ")") checkpoints)) " |")
        divider (str "|" (str/join "|" (repeat (+ 7 (count checkpoints)) "---")) "|")]
    (str/join
     "\n"
     (concat
      ["# E3 — transient collapse scaling"
       ""
       "Reproduce: `clojure -M src/mmca/experiments/codex-3.clj > holes/E3-results.md`"
       "Determinism gate: `clojure -M:test`."
       (format "Config: feedforward offset+4, q=%s, seeds %d–%d (%d per q/width), widths %s, T=%d, collapse = first t with <=%d distinct rules."
               (pr-str (:qs config)) (first (:seeds config)) (second (:seeds config))
               (:seed-count config) (pr-str (:widths config)) (:steps config)
               (:collapse-threshold config))
       ""
       "## Survival and collapse-time quantiles"
       ""
       header divider]
      (for [{:keys [q width n collapsed censored quantiles survival]} widths]
        (str "| " (fmt q) " | " width " | " collapsed "/" n " | " censored " | "
             (quantile-cell (:q25 quantiles) (:steps config)) " | "
             (quantile-cell (:q50 quantiles) (:steps config)) " | "
             (quantile-cell (:q75 quantiles) (:steps config)) " | "
             (str/join " | " (map #(fmt (get survival %)) checkpoints)) " |"))
      ["" "## Discrete interval hazard P(lo < T <= hi | T > lo)" ""]
      (mapcat
       (fn [{:keys [q width hazard]}]
         (concat [(str "- **q=" (fmt q) ", L=" width "**:")]
                 [(str "  " (str/join "; "
                                      (map (fn [{:keys [from to hazard]}]
                                             (format "(%d,%d]=%.3f" from to hazard))
                                           hazard))) ]))
       widths)
      ["" "## Median scaling fits" ""
       "Fits use only widths with an observed median; R² ranks these four-point descriptive hypotheses and is not an asymptotic-law claim."
       ""]
      (if (some val fits)
        (for [[q models] fits
              [model {:keys [slope intercept r2]}] models]
          (format "- **q=%.3f, %s**: slope %.6f, intercept %.6f, R² %.4f"
                  (double q) (name model) slope intercept r2))
        ["- No fit: fewer than three widths had an observed median."])
      [""
       "## Reading"
       ""
       (str "Across q the offset+4 transient is broad and width-dependent; the table reports the censoring rather than treating T as a collapse. "
            (if censored-median?
              "At least one median remains right-censored, so these data cannot distinguish exponential scaling from a persistent active phase within the stated horizon. "
              (str "All medians are observed; the best finite-width fits by q are "
                   (str/join ", " (map (fn [[q model]] (str q "→" (name model))) best-fits))
                   ". These are model-ranking clues, not evidence of an asymptotic law. "))
            "The interval hazards show whether collapse risk is concentrated early or remains memoryless-looking across the tail. The scan uses E2's independent seeded interrupter tape, so changing q does not perturb the propagator source-position tape.")
       ""]))))

(defn -main [& _]
  (println (render-report (run-experiment default-config))))
