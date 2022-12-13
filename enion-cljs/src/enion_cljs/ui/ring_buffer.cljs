(ns enion-cljs.ui.ring-buffer)

(deftype RingBuffer
  [start len buf meta]

  ICounted

  (-count [this] len)


  ISequential ; tagging interface

  IWithMeta

  (-with-meta
    [this m]
    (RingBuffer. start len buf m))


  IMeta

  (-meta [this] meta)


  IStack

  (-peek
    [this]
    (nth buf (rem start (count buf))))


  (-pop
    [this]
    (if (zero? len)
      (throw (js/Error. "Can't pop empty queue"))
      (RingBuffer. (rem (inc start) (count buf)) (dec len) (assoc buf start nil) meta)))


  IEmptyableCollection

  (-empty
    [this]
    (RingBuffer. 0 0 (vec (repeat (count buf) nil)) meta))


  IEquiv

  (-equiv
    [this other]
    (and (sequential? other)
         (or (not (counted? other))
             (= (count this) (count other)))
         (= (seq this) (seq other))))


  ICollection

  (-conj
    [this x]
    (if (= len (count buf))
      (RingBuffer. (rem (inc start) len) len (assoc buf start x) meta)
      (RingBuffer. start (inc len) (assoc buf (rem (+ start len) (count buf)) x) meta)))


  ISeqable

  (-seq
    [this]
    (seq (for [i (range len)]
           (nth buf (rem (+ start i) (count buf))))))


  IReversible

  (-rseq
    [this]
    (seq (for [i (range (- len 1) -1 -1)]
           (nth buf (rem (+ start i) (count buf))))))


  IIndexed

  (-nth
    [this i]
    (nth buf (mod (+ start i) len)))


  (-nth
    [this i default]
    (if (< (max i (- i)) len)
      (nth buf (mod (+ start i) len))
      default))


  IPrintWithWriter

  (-pr-writer
    [b w opts]
    (-write w "(")
    (loop [b (seq b)]
      (when-let [[x & xs] b]
        (-write w x)
        (when xs
          (-write w " ")
          (recur xs))))
    (-write w ")")))

(defn ring-buffer
  "Create an empty ring buffer with the specified [capacity]."
  [capacity]
  (RingBuffer. 0 0 (vec (repeat capacity nil)) nil))
