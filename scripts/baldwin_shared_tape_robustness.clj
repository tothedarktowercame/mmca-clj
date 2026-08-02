(ns baldwin-shared-tape-robustness
  "Three-tape robustness amendment for the shared-tape context diagnostic."
  (:require [clojure.java.io :as io]
            [mmca.baldwin-mechanism :as mechanism]
            [mmca.baldwin-selection :as selection]))

(def environment-seeds [1 2 3 4 5 6 7 8])
(def rewrite-seeds [20260811 20260812 20260813])
(def baseline-stable-contexts 4)
(def baseline-capture-basis-points 2003)
(def fixed-p0
  "01011111101100010000000111010011001010110101011111011001100001010010111111100101")

(defn sha256-file [path]
  (let [digest (java.security.MessageDigest/getInstance "SHA-256")]
    (with-open [input (io/input-stream path)]
      (let [buffer (byte-array 65536)]
        (loop []
          (let [n (.read input buffer)]
            (when (pos? n)
              (.update digest buffer 0 n)
              (recur))))))
    (format "%064x" (BigInteger. 1 (.digest digest)))))

(defn tape-result [genome rewrite-seed]
  (let [rewrite-schedule (repeat 8 rewrite-seed)
        profiles (mapv #(mechanism/context-profile genome %1 %2 nil)
                       environment-seeds rewrite-schedule)
        fixed-profiles (mapv #(mechanism/context-profile genome %1 %2 fixed-p0)
                             environment-seeds rewrite-schedule)
        summary (mechanism/context-invariance-summary profiles)]
    {:rewrite-seed rewrite-seed
     :context-count (:context-count summary)
     :stable-contexts (:stable-contexts summary)
     :capture-numerator (:capture-numerator summary)
     :capture-denominator (:capture-denominator summary)
     :capture-basis-points (:capture-basis-points summary)
     :fixed-apparatus-exact (apply = fixed-profiles)
     :profiles profiles
     :fixed-profiles fixed-profiles}))

(defn -main [& [record-path output-path source-revision expected-input-sha]]
  (when-not (and record-path output-path source-revision expected-input-sha)
    (throw (ex-info
            (str "usage: baldwin_shared_tape_robustness.clj RECORD OUTPUT "
                 "SOURCE_REVISION EXPECTED_INPUT_SHA256") {})))
  (let [observed-sha (sha256-file record-path)
        _ (when-not (= expected-input-sha observed-sha)
            (throw (ex-info "input checksum does not match registration"
                            {:expected expected-input-sha :observed observed-sha})))
        genome (selection/best-genome-from-record record-path)
        results (mapv #(tape-result genome %) rewrite-seeds)
        classification (mechanism/classify-context-robustness
                        results baseline-stable-contexts
                        baseline-capture-basis-points)
        report {:kind :baldwin-shared-tape-robustness
                :schema 1
                :source-revision source-revision
                :input-sha256 observed-sha
                :environment-seeds environment-seeds
                :rewrite-seeds rewrite-seeds
                :baseline {:stable-contexts baseline-stable-contexts
                           :capture-basis-points baseline-capture-basis-points}
                :results results
                :classification classification}]
    (spit output-path (str (pr-str report) "\n"))
    (println
     (pr-str
      {:kind (:kind report)
       :results (mapv #(select-keys % [:rewrite-seed :stable-contexts
                                       :capture-basis-points
                                       :fixed-apparatus-exact]) results)
       :classification classification}))))

(apply -main *command-line-args*)
