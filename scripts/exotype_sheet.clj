;; Regenerate a Part IV phenotype/genotype sheet from the exotype engine.
;;
;;   clojure -M scripts/exotype_sheet.clj <beta> <kappa> <seed> <steps> <out-prefix> [--genotype]
;;
;; beta  = policy precision of the EFE selection rule (the paper's Part III coordinate)
;; kappa = epistemic weight
;; Writes <out-prefix>-phe.txt, and with --genotype also <out-prefix>-gen.txt.
;; Deterministic: same arguments give byte-identical output.
(ns exotype-sheet
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def width 250)

(defn- initial-components [seed]
  (ca/with-seed seed
    {:genotype (vec (ca/random-sigil-string width))
     :phenotype (ca/random-phenotype-string width)
     :exotypes (grid/initial-grid :heterogeneous-fixed width)}))

(defn metaca-state [beta kappa seed]
  (let [{:keys [genotype phenotype exotypes]} (initial-components seed)]
    {:arm :efe-full :seed seed :time 0
     :self-tuning-arm :fixed-0.55 :lambda-step-size 0.0
     :lambdas (vec (repeat width 0.55))
     :genotype genotype :previous-genotype genotype
     :phenotype phenotype :exotypes exotypes
     :blend-action? true :epistemic-coefficient kappa
     :blend-strength 0.0 :apply-probability 1.0
     :policy-precision beta}))

(defn checked-step
  "Guards the two grid coordinates and the arm; a silent change in any of them
   would alter the trajectory without any error surfacing."
  [state]
  (let [advanced (tuning/step state)]
    (doseq [k [:policy-precision :epistemic-coefficient]]
      (when-not (= (double (k state)) (double (k advanced)))
        (throw (ex-info "MetaCA step failed to preserve a grid coordinate" {:key k}))))
    (when-not (= :efe-full (:arm advanced))
      (throw (ex-info "MetaCA step changed the EFE arm" {})))
    advanced))

(defn -main [& args]
  (let [[b k s n prefix & flags] args
        steps (Integer/parseInt n)
        geno? (some #{"--genotype"} flags)
        pw (io/writer (str prefix "-phe.txt"))
        gw (when geno? (io/writer (str prefix "-gen.txt")))]
    (assert (instance? Character (first (:phenotype (metaca-state 8.0 0.1 1))))
            "phenotype must be a Character sequence")
    (try
      (loop [st (metaca-state (Double/parseDouble b) (Double/parseDouble k)
                              (Long/parseLong s))
             row 0]
        (when (< row steps)
          (let [adv (checked-step st)]
            (.write pw (str (apply str (:phenotype adv)) "\n"))
            (when gw (.write gw (str (str/join " " (map hash (:genotype adv))) "\n")))
            (recur adv (inc row)))))
      (finally (.close pw) (when gw (.close gw))))
    (println "wrote" (str prefix "-phe.txt") (when geno? (str prefix "-gen.txt")))))
(apply -main *command-line-args*)
