(ns baldwin-shared-tape-context
  "Preregistered shared-rewrite-tape context-invariance diagnostic."
  (:require [clojure.java.io :as io]
            [mmca.baldwin-mechanism :as mechanism]
            [mmca.baldwin-selection :as selection]))

(def environment-seeds [1 2 3 4 5 6 7 8])
(def common-rewrite-seed 20260802)
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

(defn profiles [genome rewrite-seeds fixed]
  (mapv (fn [environment-seed rewrite-seed]
          (mechanism/context-profile genome environment-seed rewrite-seed fixed))
        environment-seeds rewrite-seeds))

(defn -main [& [record-path output-path source-revision expected-input-sha]]
  (when-not (and record-path output-path source-revision expected-input-sha)
    (throw (ex-info
            (str "usage: baldwin_shared_tape_context.clj RECORD OUTPUT "
                 "SOURCE_REVISION EXPECTED_INPUT_SHA256") {})))
  (let [observed-sha (sha256-file record-path)
        _ (when-not (= expected-input-sha observed-sha)
            (throw (ex-info "input checksum does not match registration"
                            {:expected expected-input-sha :observed observed-sha})))
        genome (selection/best-genome-from-record record-path)
        variable (profiles genome environment-seeds nil)
        shared (profiles genome (repeat 8 common-rewrite-seed) nil)
        fixed-shared (profiles genome (repeat 8 common-rewrite-seed) fixed-p0)
        variable-summary (mechanism/context-invariance-summary variable)
        shared-summary (mechanism/context-invariance-summary shared)
        apparatus-exact (apply = fixed-shared)
        classification (mechanism/classify-shared-tape-context
                        variable-summary shared-summary)
        report {:kind :baldwin-shared-tape-context
                :schema 1
                :source-revision source-revision
                :input-sha256 observed-sha
                :environment-seeds environment-seeds
                :common-rewrite-seed common-rewrite-seed
                :fixed-p0 fixed-p0
                :variable-summary variable-summary
                :shared-summary shared-summary
                :fixed-p0-shared-tape-exact apparatus-exact
                :classification classification
                :variable-profiles variable
                :shared-profiles shared
                :fixed-p0-shared-profiles fixed-shared}]
    (spit output-path (str (pr-str report) "\n"))
    (println
     (pr-str
      {:kind (:kind report)
       :variable-stable-contexts (:stable-contexts variable-summary)
       :variable-capture-basis-points (:capture-basis-points variable-summary)
       :shared-stable-contexts (:stable-contexts shared-summary)
       :shared-capture-basis-points (:capture-basis-points shared-summary)
       :fixed-p0-shared-tape-exact apparatus-exact
       :classification classification}))))

(apply -main *command-line-args*)
