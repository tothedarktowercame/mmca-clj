(ns mmca.baldwin-search-analysis
  "Precommitted interpretation of a completed Baldwin search battery."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [mmca.baldwin-preregistration :as prereg]
            [mmca.baldwin-spec :as spec]))

(def selected-arms
  [:independent-variable :coupled-variable :independent-fixed :coupled-fixed])

(defn- read-records [path]
  (->> (str/split-lines (slurp path))
       (remove str/blank?)
       (mapv edn/read-string)))

(defn treatment-separated? [path-a path-b]
  (let [signature (fn [path]
                    (->> (read-records path)
                         (filter #(and (nil? (:kind %)) (pos? (:gen %))))
                         (mapv #(select-keys % [:gen :field :hold]))))]
    (not= (signature path-a) (signature path-b))))

(defn- lineage [individual by-generation-id]
  (loop [current individual path ()]
    (let [generation (:gen current)
          path' (conj path current)]
      (if (zero? generation)
        (vec path')
        (let [previous-generation (dec generation)
              predecessor
              (or (get by-generation-id [previous-generation (:id current)])
                  (some->> (:parent current)
                           (vector previous-generation)
                           (get by-generation-id)))]
          (if predecessor
            (recur predecessor path')
            (vec path')))))))

(defn- candidate [records endpoint threshold]
  (let [individuals (filterv #(nil? (:kind %)) records)
        by-generation-id (into {} (map (juxt (juxt :gen :id) identity) individuals))
        finish (get by-generation-id [(:gen endpoint) (:id endpoint)])
        chain (when finish (lineage finish by-generation-id))
        trajectory
        (mapv (fn [individual]
                {:genome {:field (:field individual)
                          :hold (mapv #(= 1 %) (:hold individual))}
                 :performance (:reach individual)
                 :dependence (:dependence individual)})
              chain)
        trajectory' (if (seq trajectory)
                      (assoc-in trajectory [(dec (count trajectory)) :inherited-performance]
                                (:held-reach endpoint))
                      trajectory)
        failures (spec/witness-failures trajectory' threshold (constantly true))]
    {:endpoint-id (:id endpoint)
     :generation (:gen endpoint)
     :lineage-length (count trajectory')
     :failures (vec failures)
     :witness? (empty? failures)}))

(defn analyze-arm [path threshold]
  (let [records (read-records path)
        endpoints (filterv #(= :endpoint (:kind %)) records)
        candidates (mapv #(candidate records % threshold) endpoints)
        witness (first (filter :witness? candidates))
        individuals (filterv #(nil? (:kind %)) records)
        final-generation (apply max (map :gen individuals))
        final (filterv #(= final-generation (:gen %)) individuals)]
    {:witness? (boolean witness)
     :witness witness
     :candidate-failures (frequencies (mapcat :failures candidates))
     :final-generation final-generation
     :mean-reach (/ (reduce + (map :reach final)) (double (count final)))
     :mean-dependence (/ (reduce + (map :dependence final)) (double (count final)))
     :mean-held (/ (reduce + (map (fn [g]
                                    (/ (reduce + (:hold g))
                                       (double (count (:hold g)))))
                                  final))
                   (double (count final)))}))

(defn classify [arms]
  (cond
    (get-in arms [:independent-variable :witness?]) :baseline-assimilation
    (get-in arms [:coupled-fixed :witness?]) :interaction
    (get-in arms [:coupled-variable :witness?]) :coordination-bottleneck
    (get-in arms [:independent-fixed :witness?]) :moving-target-bottleneck
    :else :no-tested-repair))

(defn analyze [registration-path directory]
  (let [registration (prereg/read-registration registration-path)
        threshold (get-in registration [:production-protocol :witness-reach-threshold])
        arms (into {}
                   (map (fn [arm]
                          [arm (analyze-arm (io/file directory (str (name arm) ".edn"))
                                            threshold)])
                        selected-arms))]
    {:kind :baldwin-search-result
     :schema 1
     :witness-reach-threshold threshold
     :arms arms
     :outcome (classify arms)}))

(defn -main [& [registration-path directory output-path]]
  (when-not (every? some? [registration-path directory output-path])
    (throw (ex-info "usage: analyze_baldwin_search.clj REGISTRATION DIRECTORY OUTPUT" {})))
  (let [result (analyze registration-path directory)]
    (spit output-path (str (pr-str result) "\n"))
    (println (pr-str result))))
