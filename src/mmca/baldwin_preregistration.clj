(ns mmca.baldwin-preregistration
  "Executable validator for the prospective Baldwin search preregistration.

   Validation and launch permission are separate.  The committed manifest can be
   structurally valid while remaining deliberately blocked until the new treatments
   have produced a revision-bound smoke trace."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]))

(def required-arm-ids
  #{:neutral :independent-variable :coupled-variable
    :independent-fixed :coupled-fixed})

(def required-factorial
  #{[:independent :variable] [:coupled :variable]
    [:independent :fixed] [:coupled :fixed]})

(def required-observations
  #{:independent-mutation-observed :coupled-mutation-observed
    :variable-p0-observed :fixed-p0-observed :shared-random-tape
    :configuration-valid :positive-control-passed :treatment-separated
    :artifacts-complete})

(def required-stop-rules
  #{:invalid-configuration :failed-positive-control :inert-treatment
    :missing-artifacts :deadline})

(def required-outcomes
  #{:baseline-assimilation :coordination-bottleneck
    :moving-target-bottleneck :interaction :no-tested-repair})

(defn read-registration [path]
  (edn/read-string (slurp path)))

(defn failures [registration]
  (let [arms (:arms registration)
        selected (filter :selection? arms)
        pilot (set (:pilot-seeds registration))
        confirmation (set (:confirmation-seeds registration))]
    (cond-> []
      (not= :baldwin-search-preregistration (:kind registration))
      (conj :wrong-kind)

      (not= 1 (:schema registration))
      (conj :wrong-schema)

      (empty? pilot)
      (conj :missing-pilot-seed)

      (empty? confirmation)
      (conj :missing-confirmation-seed)

      (seq (set/intersection pilot confirmation))
      (conj :pilot-reused-for-confirmation)

      (not= required-arm-ids (set (map :id arms)))
      (conj :wrong-arms)

      (not= required-factorial
            (set (map (juxt :mutation :p0) selected)))
      (conj :incomplete-factorial)

      (not= 1 (count (filter #(false? (:selection? %)) arms)))
      (conj :missing-unique-neutral-arm)

      (not= required-observations (set (:required-smoke-observations registration)))
      (conj :wrong-smoke-observations)

      (not= required-stop-rules (set (:stop-rules registration)))
      (conj :wrong-stop-rules)

      (not= required-outcomes (set (:outcomes registration)))
      (conj :wrong-outcomes)

      (> (:estimated-minutes registration) (:budget-minutes registration))
      (conj :over-budget))))

(defn launchable?
  "A valid document is still blocked until a smoke receipt contains every required
   positive observation.  Missing keys fail closed."
  [registration smoke]
  (and (empty? (failures registration))
       (= :smoke-passed (:implementation-status registration))
       (every? true? (map smoke required-observations))))

(defn report [registration smoke]
  (let [problems (failures registration)]
    {:kind :baldwin-search-preregistration-validation
     :valid? (empty? problems)
     :implementation-status (:implementation-status registration)
     :failures problems
     :launchable? (launchable? registration smoke)}))

(defn -main [& [registration-path smoke-path]]
  (when-not registration-path
    (throw (ex-info "usage: validate_baldwin_preregistration.clj REGISTRATION [SMOKE]" {})))
  (let [registration (read-registration registration-path)
        smoke (if smoke-path (read-registration smoke-path) {})
        result (report registration smoke)]
    (println (pr-str result))
    (when-not (:valid? result)
      (System/exit 1))))
