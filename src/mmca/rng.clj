(ns mmca.rng
  "Pure-Clojure reproduction of the GNU Emacs 30/Linux random stream.

  The Tier-1 engine seeds `(random (format \"prop-%d\" seed))`. On GNU/Linux,
  Emacs folds that string into a 32-bit seed, uses glibc's additive `random`,
  and combines two 31-bit draws into each 62-bit Emacs draw. Keeping that
  stream here makes the Clojure port grid-identical without invoking Emacs."
  (:refer-clojure :exclude [rand-int]))

(def ^:private uint32-mask 0xffffffff)
(def ^:private int-mask (dec (bit-shift-left 1 61)))

(defn- u32 [n]
  (bit-and (long n) uint32-mask))

(defn string-seed
  "Fold UTF-8 bytes as Emacs `seed_random` does for a 32-bit random_seed."
  [s]
  (let [bytes (.getBytes (str s) "UTF-8")
        folded (reduce (fn [acc i]
                         (update acc (mod i 4)
                                 bit-xor (bit-and 0xff (aget bytes i))))
                       [0 0 0 0]
                       (range (alength bytes)))]
    (reduce-kv (fn [n i b]
                 (bit-or n (bit-shift-left (long b) (* 8 i))))
               0
               folded)))

(defn- glibc-state
  "Return glibc random's degree-31 state after its 344-value warm-up."
  [seed]
  (let [seed (long (if (zero? seed) 1 seed))]
    (loop [xs [seed]
           i 1]
      (cond
        (< i 31)
        (recur (conj xs (mod (* 16807 (peek xs)) 2147483647)) (inc i))

        (< i 34)
        (recur (conj xs (nth xs (- i 31))) (inc i))

        (< i 344)
        (recur (conj xs (u32 (+ (nth xs (- i 31))
                                 (nth xs (- i 3)))))
               (inc i))

        :else xs))))

(defrecord EmacsRng [state index])

(defn- glibc-random! [^EmacsRng rng]
  (let [i @(.index rng)
        xs @(.state rng)
        next (u32 (+ (nth xs (- i 31)) (nth xs (- i 3))))]
    (swap! (.state rng) conj next)
    (swap! (.index rng) inc)
    (unsigned-bit-shift-right next 1)))

(defn draw!
  "Return the nonnegative random payload used by Emacs for bounded draws."
  [^EmacsRng rng]
  (let [v (loop [i 0
                 v 0]
            (if (= i 2)
              v
              (let [r (glibc-random! rng)
                    shifted-left (bit-shift-left v 31)
                    shifted-right (unsigned-bit-shift-right v 33)]
                (recur (inc i)
                       (bit-xor r shifted-left shifted-right)))))
        mixed (bit-xor v (unsigned-bit-shift-right v 2))]
    (bit-and mixed int-mask)))

(defn make-rng
  "Create the stream produced after evaluating `(random seed-string)`.

  The seeding call itself returns and therefore consumes one random value."
  [seed-string]
  (let [rng (->EmacsRng (atom (glibc-state (string-seed seed-string)))
                        (atom 344))]
    (draw! rng)
    rng))

(defn rand-int
  "Match Emacs `(random limit)`, including rejection of modulo bias."
  [rng limit]
  {:pre [(pos-int? limit)]}
  (let [limit (long limit)
        difference-limit (- int-mask limit -1)]
    (loop []
      (let [r (draw! rng)
            remainder (mod r limit)
            difference (- r remainder)]
        (if (< difference-limit difference)
          (recur)
          remainder)))))

(defn rand-double
  "Return a deterministic uniform double in [0,1) from the Emacs stream."
  [rng]
  (/ (double (draw! rng)) 2305843009213693952.0))
