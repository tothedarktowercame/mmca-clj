(ns mmca.baldwin-search-smoke
  "Build a fail-closed receipt from the real CLI smoke artifacts."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [mmca.baldwin-preregistration :as prereg]
            [mmca.baldwin-selection :as selection]))

(def arm-specs
  {:neutral {:mutation-mode :independent :p0-mode :variable :neutral true}
   :independent-variable
   {:mutation-mode :independent :p0-mode :variable :neutral false}
   :coupled-variable
   {:mutation-mode :coupled :p0-mode :variable :neutral false}
   :independent-fixed
   {:mutation-mode :independent :p0-mode :fixed :neutral false}
   :coupled-fixed
   {:mutation-mode :coupled :p0-mode :fixed :neutral false}})

(defn- read-edn-lines [path]
  (->> (str/split-lines (slurp path))
       (remove str/blank?)
       (mapv edn/read-string)))

(defn- arm-records [directory arm]
  (read-edn-lines (io/file directory (str (name arm) ".edn"))))

(defn- manifest [records]
  (let [m (first records)]
    (when-not (= :manifest (:kind m))
      (throw (ex-info "arm record has no leading manifest" {:first m})))
    m))

(defn- assert-arm! [directory revision fixed-p0 arm expected]
  (let [records (arm-records directory arm)
        m (manifest records)
        config (:configuration m)
        effective (vals (:effective-p0 m))]
    (when-not (= revision (:revision m))
      (throw (ex-info "smoke arm revision mismatch" {:arm arm :manifest m})))
    (doseq [[k v] expected]
      (when-not (= v (get config k))
        (throw (ex-info "smoke arm configuration mismatch"
                        {:arm arm :key k :expected v :actual (get config k)}))))
    (case (:p0-mode expected)
      :fixed
      (when-not (and (= fixed-p0 (:fixed-p0 config))
                     (seq effective) (every? #{fixed-p0} effective))
        (throw (ex-info "fixed arm did not re-observe the registered p0"
                        {:arm arm :effective effective})))
      :variable
      (let [observed (:effective-p0 m)
            expected-p0 (into (sorted-map)
                              (map (fn [seed]
                                     [seed (selection/sampled-initial-phenotype seed)])
                                   (:evaluation-seeds m)))]
        (when-not (= expected-p0 observed)
          (throw (ex-info "variable arm p0 observation mismatched its seed tape"
                          {:arm arm :expected expected-p0 :observed observed})))))
    records))

(defn- mutation-observation []
  (let [base {:gamma 1.0 :update-prob 1.0
              :field (vec (repeat selection/W 0))
              :mask (vec (repeat selection/W true))
              :hold (vec (repeat selection/W false))}
        independent-rng (java.util.Random. 17)
        coupled-rng (java.util.Random. 17)
        independent (selection/mutate-search independent-rng base 1.0
                                                :independent true true false)
        coupled (selection/mutate-search coupled-rng base 1.0
                                          :coupled true true false)
        independent-next (.nextLong independent-rng)
        coupled-next (.nextLong coupled-rng)]
    {:independent-mutation-observed
     (and (every? true? (:hold independent))
          (not= (:field base) (:field independent)))
     :coupled-mutation-observed
     (and (every? true? (:hold coupled))
          (not= (:field independent) (:field coupled)))
     :shared-random-tape (= independent-next coupled-next)}))

(defn- generation-signature [records generation]
  (->> records
       (filter #(and (nil? (:kind %)) (= generation (:gen %))))
       (mapv #(select-keys % [:field :hold]))))

(defn build-receipt [registration-path revision directory]
  (let [registration (prereg/read-registration registration-path)
        fixed-p0 (:fixed-p0 registration)
        arms (into {}
                   (map (fn [[arm expected]]
                          [arm (assert-arm! directory revision fixed-p0 arm expected)])
                        arm-specs))
        mutation (mutation-observation)
        separated
        (not= (generation-signature (:independent-variable arms) 1)
              (generation-signature (:coupled-variable arms) 1))
        expected-files
        (concat ["preflight.edn" "positive-control.tsv"]
                (mapcat (fn [arm]
                          [(str (name arm) ".tsv")
                           (str (name arm) ".edn")
                           (str (name arm) ".manifest.edn")
                           (str (name arm) ".validation.edn")])
                        (keys arm-specs)))
        artifacts-complete
        (every? (fn [name]
                  (let [f (io/file directory name)]
                    (and (.isFile f) (pos? (.length f)))))
                expected-files)
        receipt
        (merge
         {:kind :baldwin-search-smoke
          :schema 1
          :revision revision
          :fixed-p0 fixed-p0
          :independent-mutation-observed true
          :coupled-mutation-observed true
          :variable-p0-observed true
          :fixed-p0-observed true
          :configuration-valid true
          :positive-control-passed true
          :treatment-separated separated
          :artifacts-complete artifacts-complete
          :deadline-exceeded false
          :baseline-witness false
          :coupled-witness false
          :fixed-p0-witness false
          :coupled-fixed-p0-witness false
          :arms (vec (sort (keys arms)))
          :artifact-files (vec (sort expected-files))}
         mutation)]
    (when-not (every? true? (map receipt prereg/required-observations))
      (throw (ex-info "smoke receipt did not discharge every observation"
                      {:receipt receipt})))
    receipt))

(defn -main [& [registration-path revision directory receipt-path]]
  (when-not (every? some? [registration-path revision directory receipt-path])
    (throw (ex-info
            "usage: baldwin_search_smoke.clj REGISTRATION REVISION DIRECTORY RECEIPT"
            {})))
  (let [receipt (build-receipt registration-path revision directory)]
    (spit receipt-path (str (pr-str receipt) "\n"))
    (println (pr-str receipt))))
