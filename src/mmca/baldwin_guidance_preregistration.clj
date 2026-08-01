(ns mmca.baldwin-guidance-preregistration
  "Executable structural validator for the Lean guidance preregistration."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]))

(def required-task-partition
  {:training
   {:specification "metaca-guidance-tasks-v1/train/seeds=1..3/sites=0,20,40,60"
    :seeds [1 2 3]
    :sites [0 20 40 60]}
   :held-out
   {:specification "metaca-guidance-tasks-v1/heldout/seeds=101..103/sites=0,20,40,60"
    :seeds [101 102 103]
    :sites [0 20 40 60]}})

(def required-learning-budgets [0 4 16 64 120])
(def required-lean-revision "f50d34cffbd2d92b624592ef50e9d57f7b84af98")
(def required-preparedness
  {:metric :population-mean-band-score
   :budgets [0 4 16 64]
   :functional-budget 120
   :functional-mean-reach-threshold 10.0
   :guidance-rule
   "The learning-evolved final population must have strictly greater preparedness than the no-learning-evolved final population on both training and held-out tasks."})

(def required-production-protocol
  {:mode "guidance-field"
   :generations 40
   :population 32
   :evaluation-seeds 3
   :evaluation-sites 4
   :training-tasks 12
   :held-out-tasks 12
   :cost 0.0
   :hgt false
   :field-rate 0.02
   :warmup 0
   :p0-mode :variable
   :mutation-mode :legacy
   :seed-offset 0
   :pilot-evolution-seed 20260802
   :arm-timeout-minutes 80})

(def required-smoke-configuration-evidence
  {:status :post-smoke-clarification
   :reason
   "These runner defaults were used by both byte-identical smoke repetitions but omitted from the initial production-protocol record; they are recorded before the paid pilot without changing a value."
   :manifest-sha256
   {:learning-evolution
    "ea6f0d78f22b8aa09cc149948f6fa14cbf8fe04e222d7ff14e7fb5be3d7ab77e"
    :mutation-only
    "01ef52886efcfbac46e394552addb89d7f906882e8ad900a2f33aaa996a90748"
    :no-learning-evolution
    "61491f7aea372901ae1ed0323c5dce0ad31fa76a78581997d83c1d3c30b985c7"}})

(def required-arms
  #{{:id :mutation-only :selection? false :learning-budget 120}
    {:id :no-learning-evolution :selection? true :learning-budget 0}
    {:id :learning-evolution :selection? true :learning-budget 120}})

(def required-observations
  #{:learning-enabled-observed :learning-disabled-observed
    :paired-genetic-tape :paired-evaluation-tape
    :task-partition-observed :learning-budgets-observed
    :configuration-valid :positive-control-passed :treatment-separated
    :artifacts-complete})

(def required-stop-rules
  #{:invalid-configuration :failed-positive-control :inert-treatment
    :missing-artifacts :deadline})

(def required-outcomes
  #{:guidance-and-assimilation :guidance-only
    :assimilation-without-guidance :neither-certified})

(defn read-edn [path]
  (edn/read-string (slurp path)))

(defn failures [registration]
  (let [pilot (set (:pilot-seeds registration))
        confirmation (set (:confirmation-seeds registration))]
    (cond-> []
      (not= :baldwin-guidance-preregistration (:kind registration))
      (conj :wrong-kind)
      (not= 1 (:schema registration))
      (conj :wrong-schema)
      (not= "DarkTower.BaldwinGuidancePreregistration.experiment"
            (:lean-registration registration))
      (conj :wrong-lean-registration)
      (not= required-lean-revision (:lean-revision registration))
      (conj :wrong-lean-revision)
      (not= required-task-partition (:task-partition registration))
      (conj :wrong-task-partition)
      (not= required-learning-budgets (:learning-budgets registration))
      (conj :wrong-learning-budgets)
      (not= required-preparedness (:preparedness registration))
      (conj :wrong-preparedness)
      (not= required-production-protocol (:production-protocol registration))
      (conj :wrong-production-protocol)
      (not= required-smoke-configuration-evidence
            (:smoke-configuration-evidence registration))
      (conj :wrong-smoke-configuration-evidence)
      (empty? pilot)
      (conj :missing-pilot-seed)
      (empty? confirmation)
      (conj :missing-confirmation-seed)
      (seq (set/intersection pilot confirmation))
      (conj :pilot-reused-for-confirmation)
      (not= required-arms (set (:arms registration)))
      (conj :wrong-arms)
      (not= required-observations (set (:required-smoke-observations registration)))
      (conj :wrong-smoke-observations)
      (not= required-stop-rules (set (:stop-rules registration)))
      (conj :wrong-stop-rules)
      (not= required-outcomes (set (:outcomes registration)))
      (conj :wrong-outcomes)
      (> (:estimated-minutes registration) (:budget-minutes registration))
      (conj :over-budget))))

(defn launchable? [registration smoke]
  (and (empty? (failures registration))
       (= :smoke-passed (:implementation-status registration))
       (not (str/blank? (:implementation-revision registration)))
       (not (str/blank? (:lean-revision registration)))
       (= (:implementation-revision registration) (:revision smoke))
       (= (:lean-revision registration) (:lean-revision smoke))
       (= required-task-partition (:task-partition smoke))
       (= required-learning-budgets (:learning-budgets smoke))
       (every? true? (map smoke required-observations))))

(defn report [registration smoke]
  (let [problems (failures registration)]
    {:kind :baldwin-guidance-preregistration-validation
     :valid? (empty? problems)
     :implementation-status (:implementation-status registration)
     :failures problems
     :launchable? (launchable? registration smoke)}))

(defn -main [& [registration-path smoke-path]]
  (when-not registration-path
    (throw (ex-info
            "usage: validate_baldwin_guidance_preregistration.clj REGISTRATION [SMOKE]"
            {})))
  (let [registration (read-edn registration-path)
        smoke (if smoke-path (read-edn smoke-path) {})
        result (report registration smoke)]
    (println (pr-str result))
    (when-not (:valid? result)
      (System/exit 1))))
