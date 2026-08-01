(ns mmca.baldwin-guidance
  "Executable port of the population-level Baldwin GuidanceWitness.

   The scientific unit is a paired population trajectory, not a cherry-picked
   winning lineage. Preparedness is fixed by the preregistration and evaluated
   on both its training and held-out task partitions."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [mmca.baldwin-guidance-preregistration :as prereg]
            [mmca.baldwin-selection :as selection]))

(defn mean [xs]
  (when (empty? xs)
    (throw (ex-info "mean requires a nonempty collection" {})))
  (/ (reduce + (map double xs)) (double (count xs))))

(defn record-rows [path]
  (->> (str/split-lines (slurp path))
       (remove str/blank?)
       (map edn/read-string)
       (filter #(and (contains? % :gen) (nil? (:kind %))))
       vec))

(defn populations [path]
  (->> (record-rows path)
       (group-by :gen)
       (into (sorted-map))
       (mapv (fn [[generation rows]]
               {:generation generation
                :population (mapv selection/decode-record-genome rows)}))))

(defn heritable-signature [population]
  (->> population
       (sort-by :id)
       (mapv #(select-keys % [:id :gamma :update-prob :field :mask :hold]))))

(defn valid-population-path?
  "Every later individual must be either an unchanged survivor or name a parent
   in the immediately preceding population."
  [trajectory]
  (and (seq trajectory)
       (every?
        true?
        (for [[before after] (partition 2 1 trajectory)
              :let [previous-ids (set (map :id (:population before)))]]
          (and (= (inc (:generation before)) (:generation after))
               (every? (fn [{:keys [id parent]}]
                         (or (contains? previous-ids id)
                             (contains? previous-ids parent)))
                       (:population after)))))))

(defn evaluation-tape-aligned?
  "Exercise both budget branches and inspect the three RNG streams afterwards."
  []
  (let [genome (vec (repeat selection/W 0))
        phenotype (selection/sampled-initial-phenotype 7)
        run (fn [budget]
              (let [random (java.util.Random. 11)
                    gate (java.util.Random. 12)
                    update (java.util.Random. 13)]
                (selection/run-from random gate update genome phenotype
                                    selection/STEPS 1.0 1.0
                                    (vec (repeat selection/W true))
                                    (vec (repeat selection/W false))
                                    phenotype 0 budget)
                [(.nextLong random) (.nextLong gate) (.nextLong update)]))]
    (= (run 0) (run selection/STEPS))))

(defn population-summary [population {:keys [seeds sites]} budgets]
  (let [evaluations
        (mapv
         (fn [budget]
           (let [reaches
                 (->> population
                      (pmap (fn [genome]
                              (:mean
                               (selection/reach genome seeds sites
                                                {:learning-budget budget}))))
                      vec)]
             {:budget budget
              :mean-reach (mean reaches)
              :mean-band-score (mean (map selection/band-score reaches))}))
         budgets)]
    {:population-size (count population)
     :curve evaluations}))

(defn curve-value [summary budget key]
  (or (some (fn [row] (when (= budget (:budget row)) (key row)))
            (:curve summary))
      (throw (ex-info "learning budget absent from curve"
                      {:budget budget :curve (:curve summary)}))))

(defn preparedness [summary budgets]
  (mean (map #(curve-value summary % :mean-band-score) budgets)))

(defn- manifest-failures [manifest expected-budget expected-neutral training-tasks]
  (let [config (:configuration manifest)]
    (cond-> []
      (not= "guidance-field" (:mode config)) (conj :wrong-mode)
      (not= expected-budget (:learning-budget config)) (conj :wrong-learning-budget)
      (not= expected-neutral (:neutral config)) (conj :wrong-neutral-setting)
      (not= true (:gamma-pinned config)) (conj :gamma-not-pinned)
      (not= true (:plasticity-pinned config)) (conj :plasticity-not-pinned)
      (not= true (:hold-pinned config)) (conj :hold-not-pinned)
      (not= false (:hgt config)) (conj :hgt-enabled)
      (not= 0.0 (:cost config)) (conj :nonzero-cost)
      (not= (:seeds training-tasks) (:evaluation-seeds manifest))
      (conj :wrong-training-seeds)
      (not= (:sites training-tasks) (:evaluation-sites manifest))
      (conj :wrong-training-sites))))

(defn treatment-separated? [no-learning-trajectory learning-trajectory]
  (let [scores (fn [trajectory]
                 (->> (:population (first trajectory))
                      (sort-by :id)
                      (mapv (juxt :id :reach :score))))]
    (not= (scores no-learning-trajectory) (scores learning-trajectory))))

(defn analyze
  "Analyze one three-arm run directory using the immutable registration."
  [registration directory]
  (let [budgets (:learning-budgets registration)
        prep-budgets (get-in registration [:preparedness :budgets])
        functional-budget (get-in registration [:preparedness :functional-budget])
        threshold (get-in registration
                          [:preparedness :functional-mean-reach-threshold])
        tasks (:task-partition registration)
        paths (fn [arm suffix]
                (str (io/file directory (str (name arm) suffix))))
        trajectories
        (into {}
              (map (fn [arm] [arm (populations (paths arm ".edn"))]))
              [:mutation-only :no-learning-evolution :learning-evolution])
        manifests
        (into {}
              (map (fn [arm]
                     [arm (prereg/read-edn (paths arm ".manifest.edn"))]))
              [:mutation-only :no-learning-evolution :learning-evolution])
        final-population
        (fn [arm] (:population (peek (get trajectories arm))))
        summaries
        (into {}
              (for [arm [:no-learning-evolution :learning-evolution]
                    partition [:training :held-out]]
                [[arm partition]
                 (population-summary (final-population arm)
                                     (get tasks partition) budgets)]))
        no-train (get summaries [:no-learning-evolution :training])
        learning-train (get summaries [:learning-evolution :training])
        no-held (get summaries [:no-learning-evolution :held-out])
        learning-held (get summaries [:learning-evolution :held-out])
        no-train-prep (preparedness no-train prep-budgets)
        learning-train-prep (preparedness learning-train prep-budgets)
        no-held-prep (preparedness no-held prep-budgets)
        learning-held-prep (preparedness learning-held prep-budgets)
        common-initial?
        (apply = (map #(heritable-signature (:population (first (get trajectories %))))
                      [:mutation-only :no-learning-evolution :learning-evolution]))
        learning-functional?
        (<= threshold (curve-value learning-train functional-budget :mean-reach))
        no-learning-functional?
        (<= threshold (curve-value no-train functional-budget :mean-reach))
        failures
        (cond-> []
          (not common-initial?) (conj :different-initial-populations)
          (not (valid-population-path? (get trajectories :learning-evolution)))
          (conj :invalid-learning-population-path)
          (not (valid-population-path? (get trajectories :no-learning-evolution)))
          (conj :invalid-no-learning-population-path)
          (not learning-functional?) (conj :learning-population-not-functional)
          (not no-learning-functional?) (conj :no-learning-population-not-functional)
          (not (< no-train-prep learning-train-prep))
          (conj :no-training-preparedness-advantage)
          (not (< no-held-prep learning-held-prep))
          (conj :no-held-out-preparedness-advantage))
        guidance? (empty? failures)
        manifest-problems
        {:mutation-only (manifest-failures (:mutation-only manifests) 120 true
                                           (:training tasks))
         :no-learning-evolution
         (manifest-failures (:no-learning-evolution manifests) 0 false
                            (:training tasks))
         :learning-evolution
         (manifest-failures (:learning-evolution manifests) 120 false
                            (:training tasks))}
        separated?
        (treatment-separated? (get trajectories :no-learning-evolution)
                              (get trajectories :learning-evolution))]
    {:kind :baldwin-guidance-result
     :schema 1
     :guidance-certificate
     {:witness? guidance?
      :failures failures
      :common-initial-population common-initial?
      :population-paths-valid
      {:with-learning (valid-population-path? (get trajectories :learning-evolution))
       :without-learning
       (valid-population-path? (get trajectories :no-learning-evolution))}
      :functional
      {:with-learning learning-functional?
       :without-learning no-learning-functional?}
      :training-preparedness
      {:with-learning learning-train-prep :without-learning no-train-prep}
      :held-out-preparedness
      {:with-learning learning-held-prep :without-learning no-held-prep}}
     :assimilation-certificate nil
     :outcome (if guidance? :guidance-only :neither-certified)
     :task-partition tasks
     :learning-budgets budgets
     :summaries summaries
     :manifest-failures manifest-problems
     :configuration-valid (every? empty? (vals manifest-problems))
     :treatment-separated separated?
     :paired-evaluation-tape (evaluation-tape-aligned?)}))

(defn smoke-receipt
  [registration result revision lean-revision positive-control-passed artifacts]
  (let [guidance (:guidance-certificate result)
        complete? (every? #(.exists (io/file %)) artifacts)]
    {:kind :baldwin-guidance-smoke
     :schema 1
     :revision revision
     :lean-revision lean-revision
     :task-partition (:task-partition result)
     :learning-budgets (:learning-budgets result)
     :learning-enabled-observed
     (empty? (get-in result [:manifest-failures :learning-evolution]))
     :learning-disabled-observed
     (empty? (get-in result [:manifest-failures :no-learning-evolution]))
     :paired-genetic-tape (:common-initial-population guidance)
     :paired-evaluation-tape (:paired-evaluation-tape result)
     :task-partition-observed
     (= (:task-partition registration) (:task-partition result))
     :learning-budgets-observed
     (= (:learning-budgets registration) (:learning-budgets result))
     :configuration-valid (:configuration-valid result)
     :positive-control-passed positive-control-passed
     :treatment-separated (:treatment-separated result)
     :artifacts-complete complete?
     :deadline-exceeded false}))

(defn write-smoke-receipt!
  [registration-path result-path revision lean-revision output-path artifacts]
  (let [registration (prereg/read-edn registration-path)
        result (prereg/read-edn result-path)
        receipt (smoke-receipt registration result revision lean-revision true artifacts)]
    (spit output-path (str (pr-str receipt) "\n"))
    receipt))

(defn write-result! [registration-path directory output-path]
  (let [registration (prereg/read-edn registration-path)
        problems (prereg/failures registration)]
    (when (seq problems)
      (throw (ex-info "invalid guidance registration" {:failures problems})))
    (let [result (analyze registration directory)]
      (spit output-path (str (pr-str result) "\n"))
      result)))
