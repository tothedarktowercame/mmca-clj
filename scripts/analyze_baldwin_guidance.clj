(require '[mmca.baldwin-guidance :as guidance])

(let [[registration directory output] *command-line-args*]
  (when-not (and registration directory output)
    (throw (ex-info
            "usage: analyze_baldwin_guidance.clj REGISTRATION DIRECTORY OUTPUT"
            {})))
  (println (pr-str (guidance/write-result! registration directory output))))
