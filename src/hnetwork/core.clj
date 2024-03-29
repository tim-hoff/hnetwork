(ns hnetwork.core)
(use 'clojure.core.matrix) ; math
(use 'clojure.math.combinatorics) ; math
(set-current-implementation :vectorz); matrix computations

(require '[clojure.java.io :as io]); io resources
(require '[incanter.core :as i]); statistics library
(require '[incanter.datasets :as ds]); datasets, get-dataset

(defn gen-matrix
  "generates a `r` by `c` matrix with random weights between -1 and 1."
	[r c & m]
	(for [_ (take r (range))] 
   (for [_ (take c (range))] 
     (* (if (< 0.5 (rand)) -1 1) (rand)))))

(defn weight-gen 
  "generates a multitiered matrix"
  [lst]
  (loop [acc (transient []) t lst]
    (if (= 1 (count t))
           (persistent! acc)
           (recur (conj! acc (mapv #(into [] %) (apply gen-matrix t))) (drop 1 t)))))

(defn fn-fold
  "max values for a matrix"
  [fn lst]
  (loop [acc (transient []) t lst]
    (if (every? empty? t)
      (rseq (persistent! acc))
      (recur (conj! acc (apply fn (map peek t))) (map pop t))))); handle scaling for negative attributes

(defn bias
  "biases an ANN, the wrong way."
  [lst]
    (mapv (fn [x] (mapv #(+ % 0.0001) x)) lst))

(defn scaled 
  "scales between max and min properly."
  [x mn mx]
  (/ (- x mn) (- mx mn)))

(defn norm-scale
  "scales values in matrix to a range between -1 and 1, utilizing max-fld"
  [lst]
  (let [mx (fn-fold max lst)
        mn (fn-fold min lst)]
        (mapv (fn [x] (mapv scaled x mn mx)) lst)))

(defn sigmoid
  "takes in `z` and throws it in the sigmoid function\n"
  [z]
    (/ 1 (+ 1 (Math/exp (* -1 z)))))

(defn mmap
  "maps a function on a weight vector matrix"
  [function matrix]
  (mapv #(mapv function %) matrix))

(defn sigmoid-prime
  "sigmoid prime, used when feeding. yo."
  [z]
  (let [enz (Math/exp (* -1 z))]; e^(-z)])
    (/ enz (Math/pow (+ 1 enz) 2)))); enz/(1+enz)^2)

(defn pmm [m]
  "pm format wrapper, six significant digits"
  (pm m {:formatter (fn [x] (format "%.6f" (double x)))}))

(defn feed-one
  "feeds data into nn and returns adjusted weights"
  [row w lr & {:keys [training]
               :or {training false}}]
  (let [x (pop row)
        y (peek row)
        [w1 w2] w
        z2 (dot x w1)
        a2 (mapv sigmoid z2)
        [z3] (dot a2 w2)
        yhat (sigmoid z3)
        ycost (- y yhat)]
      
  (if training ; training -> adjusting weights and getting new weights
               ; else -> testing input and getting error [y yhat ycost]
               
    (let [mycost (* -1 ycost); -(y-yhat)]
          xt (transpose [x]); [[x1 x2 x3]]  to [[x1] [x2] [x3]]
          [w2t] (transpose w2)
          a2t (transpose [a2])
          sigmoid-prime-z3 (sigmoid-prime z3)
          delta-w2 (mmap #(* (* mycost sigmoid-prime-z3) %) a2t)
          lr-delta-w2 (mmap #(* % lr) delta-w2)
          new-w2 (i/minus w2 lr-delta-w2)
          sigmoid-prime-z2 (mapv sigmoid-prime z2)
          w2t-sigpz2 (mul w2t sigmoid-prime-z2)
          spzc  (* mycost sigmoid-prime-z3)
          wss (mapv #(* spzc %) w2t-sigpz2)
          delta-w1 (mapv #(let [[x] %1] (mul wss x)) xt)
          lr-delta-w1 (mmap #(* lr %) delta-w1)
          new-w1 (i/minus w1 lr-delta-w1)
          new-w [new-w1 new-w2]] new-w)
    (let [ayc (abs ycost)
          yc2 (if (> ayc 0.12) ayc 0.0)] 
      [y yhat yc2]))))

(defn feed
  "loops across input and adjustes the weights for all of it. 
  `input` assumes y values are at the end of the vectors"
  [input weight learnrate & {:keys [training] :or {training false}}]
  (let [total (count input)]
    (if training
      (loop [x input w weight]
        (if (every? empty? x)
          w
          (recur (pop x) (let [thisx (peek x)
                               cnt (count x)
                               lr (* (/ cnt total) learnrate)] 
                           (feed-one thisx w lr :training training)))))
      (loop [x input er 0.0 acc []]
        (if (every? empty? x)
          [er (/ er total) acc]
          (let [row (peek x)
                lr 0.1
                [y yhat this-error] (feed-one row weight lr :training false)]
            (recur
              (pop x)
              (+ er this-error)
              (conj acc [yhat y this-error]))))))))

(defn refeed 
  "refeeds results from training at different learning rates `lrs`"
  [data weight lrs]
  (loop [w weight lr lrs]
    (if (empty? lr)
      w
      (recur (feed data w (first lr) :training true) (rest lr)))))

(defn expand
  "expands the dataset for testing"
  [dataset magnitude]
  (loop [ds dataset m (- magnitude 1)]
    (if (<= m 0)
      (shuffle (shuffle ds))
      (recur (into [] (concat ds dataset)) (- m 1) ))))

(defn nifty-feeder
  "expands and feeds a dataset, useful for finding that special rate"
  [data magnitude lrs size  & {:keys [verbose-flag] :or {verbose-flag false}}]
  (let [dt (expand data magnitude)
        w (weight-gen size)
        w2 (refeed dt w lrs)]
    (when verbose-flag (println "Initial Weights")
      (println "w1")
      (pm (first w))
      (println "w2")
      (pm (last w)))
    w2))
