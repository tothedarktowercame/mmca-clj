(require '[mmca.baldwin-selection :as selection])

(try
  (apply selection/-main *command-line-args*)
  (finally
    (shutdown-agents)))
