(ns baldwin-guidance-smoke-receipt
  (:require [mmca.baldwin-guidance :as guidance]))

(let [[registration result revision lean-revision output & artifacts]
      *command-line-args*]
  (when-not (and registration result revision lean-revision output (seq artifacts))
    (throw (ex-info
            "usage: baldwin_guidance_smoke_receipt.clj REGISTRATION RESULT REVISION LEAN_REVISION OUTPUT ARTIFACT..."
            {})))
  (println
   (pr-str
    (guidance/write-smoke-receipt! registration result revision lean-revision
                                   output artifacts))))
