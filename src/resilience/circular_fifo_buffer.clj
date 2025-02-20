(ns resilience.circular-fifo-buffer
  (:refer-clojure :exclude [take empty?])
  (:import [io.github.resilience4j.circularbuffer CircularFifoBuffer ConcurrentCircularFifoBuffer]
           [java.util Optional]))

(defn ^CircularFifoBuffer circular-fifo-buffer [capacity]
  (ConcurrentCircularFifoBuffer. (int capacity)))

(defn size [^CircularFifoBuffer buffer]
  (.size buffer))

(defn empty? [^CircularFifoBuffer buffer]
  (.isEmpty buffer))

(defn full? [^CircularFifoBuffer buffer]
  (.isFull buffer))

(defn ->list [^CircularFifoBuffer buffer]
  (.toList buffer))

(defn add [^CircularFifoBuffer buffer element]
  (.add buffer element))

(defn ^Optional take [^CircularFifoBuffer buffer]
  (.take buffer))