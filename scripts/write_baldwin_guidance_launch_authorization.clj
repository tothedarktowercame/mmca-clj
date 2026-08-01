(ns write-baldwin-guidance-launch-authorization
  (:require [clojure.java.io :as io]
            [mmca.baldwin-guidance-preregistration :as prereg])
  (:import [java.security MessageDigest]))

(defn sha256 [path]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (with-open [input (io/input-stream path)]
      (let [buffer (byte-array 65536)]
        (loop []
          (let [n (.read input buffer)]
            (when (pos? n)
              (.update digest buffer 0 n)
              (recur))))))
    (format "%064x" (BigInteger. 1 (.digest digest)))))

(defn -main [& [registration-path smoke-path authorization-revision output]]
  (when-not (every? some?
                    [registration-path smoke-path authorization-revision output])
    (throw (ex-info
            "usage: write_baldwin_guidance_launch_authorization.clj REGISTRATION SMOKE AUTHORIZATION_REVISION OUTPUT"
            {})))
  (when-not (re-matches #"[0-9a-f]{40}" authorization-revision)
    (throw (ex-info "authorization revision must be a full commit SHA"
                    {:revision authorization-revision})))
  (let [registration (prereg/read-edn registration-path)
        smoke (prereg/read-edn smoke-path)
        report (prereg/report registration smoke)]
    (when-not (:launchable? report)
      (throw (ex-info "guidance pilot is not launchable" {:report report})))
    (spit output
          (str
           (pr-str
            {:kind :baldwin-guidance-launch-authorization
             :schema 1
             :authorization-revision authorization-revision
             :implementation-revision (:implementation-revision registration)
             :lean-revision (:lean-revision registration)
             :registration-sha256 (sha256 registration-path)
             :smoke-sha256 (sha256 smoke-path)
             :production-protocol (:production-protocol registration)
             :pilot-seed (first (:pilot-seeds registration))
             :validation report})
           "\n"))))

(apply -main *command-line-args*)
