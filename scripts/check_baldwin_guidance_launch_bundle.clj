(ns check-baldwin-guidance-launch-bundle
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.security MessageDigest]))

(defn sha256 [path]
  (let [digest (MessageDigest/getInstance "SHA-256")
        buffer (byte-array 65536)]
    (with-open [input (io/input-stream path)]
      (loop []
        (let [n (.read input buffer)]
          (when (pos? n)
            (.update digest buffer 0 n)
            (recur)))))
    (format "%064x" (BigInteger. 1 (.digest digest)))))

(let [[registration-path smoke-path authorization-path implementation-revision
       authorization-revision seed output] *command-line-args*
      registration (edn/read-string (slurp registration-path))
      smoke (edn/read-string (slurp smoke-path))
      authorization (edn/read-string (slurp authorization-path))
      passed?
      (and (= :baldwin-guidance-launch-authorization (:kind authorization))
           (= 1 (:schema authorization))
           (= implementation-revision
              (:implementation-revision authorization)
              (:implementation-revision registration)
              (:revision smoke))
           (= authorization-revision (:authorization-revision authorization))
           (= (Long/parseLong seed) (:pilot-seed authorization))
           (= (:registration-sha256 authorization) (sha256 registration-path))
           (= (:smoke-sha256 authorization) (sha256 smoke-path))
           (= (:production-protocol authorization)
              (:production-protocol registration))
           (true? (get-in authorization [:validation :launchable?])))]
  (spit output
        (str (pr-str {:kind :worker-launch-authorization-check
                      :passed? passed?
                      :implementation-revision implementation-revision
                      :authorization-revision authorization-revision}) "\n"))
  (when-not passed? (System/exit 1)))
