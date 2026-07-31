(ns check-baldwin-search-separation
  (:require [mmca.baldwin-search-analysis :as analysis]))

(let [[independent-path coupled-path] *command-line-args*]
  (when-not (and independent-path coupled-path)
    (throw (ex-info "usage: check_baldwin_search_separation.clj INDEPENDENT COUPLED" {})))
  (if (analysis/treatment-separated? independent-path coupled-path)
    (println (pr-str {:treatment-separated true}))
    (throw (ex-info "production mutation treatments were inert"
                    {:independent independent-path :coupled coupled-path}))))
