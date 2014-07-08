(ns clojurewerkz.statistiker.functions
  (:require [clojurewerkz.statistiker.fast-math :as fm]
            [clojure.core.matrix                :as matrix]
            [clojure.core.matrix.operators      :as ops]
            [schema.core                        :as s]
            [clojure.core.typed                 :as cct :refer [ann ann-record check-ns]])

  (:import [clojure.lang IFn]
           [org.apache.commons.math3.analysis MultivariateFunction MultivariateVectorFunction]
           [org.apache.commons.math3.optim InitialGuess MaxEval SimpleBounds]
           [org.apache.commons.math3.optim.nonlinear.scalar ObjectiveFunction ObjectiveFunctionGradient GoalType]
           [org.apache.commons.math3.optim.nonlinear.scalar.noderiv BOBYQAOptimizer] ;; Use optim version, not that one
           ))


(ann line
     (cct/IFn [Number Number -> (cct/IFn [Number -> Number])]))

(defn line
  "Simple linear funciton:

     f(y) = ax + b"
  [intercept slope]
  (fn [x]
    (+ intercept (* slope x))))

(ann ^:no-check fn->multivariate-function
     (cct/IFn [IFn -> MultivariateFunction]))

(defn fn->multivariate-function
  [^IFn f]
  (reify MultivariateFunction
    (value [_ v]
      (apply f (vec v)))))

(ann ^:no-check fn->multivariate-vector-function
     (cct/IFn [IFn -> MultivariateVectorFunction]))

(defn fn->multivariate-vector-function
  [^IFn f]
  (reify MultivariateVectorFunction
    (value [_ v]
      (double-array
       (apply f (vec v))))))

(ann objective-function
     (cct/IFn [IFn -> ObjectiveFunction]))

(defn objective-function
  [^IFn f]
  (ObjectiveFunction. (fn->multivariate-function f)))

(ann objective-function-gradient
     (cct/IFn [IFn -> ObjectiveFunctionGradient]))

(defn objective-function-gradient
  [^IFn f]
  (ObjectiveFunctionGradient. (fn->multivariate-vector-function f)))


;;
;; Functions
;;

(ann-record GradientProblem [objective-fn          :- ObjectiveFunction
                             objective-fn-gradient :- ObjectiveFunctionGradient])

(defrecord GradientProblem [objective-fn objective-fn-gradient])

(ann make-gradient-problem
     (cct/IFn [IFn IFn -> GradientProblem]))

(defn make-gradient-problem
  [objective gradient]
  (GradientProblem. (objective-function objective)
                    (objective-function-gradient gradient)))


(ann ^:no-check linear-fn
     (cct/IFn [(cct/Vec (cct/Vec Number)) -> (cct/IFn [Number Number -> Number ])] ))

(defn linear-fn
  "Linear function for optimizing least squares for linear regression and so forth"
  [data]
  (fn [intercept slope]
    (let [f   (line intercept slope)]
      (->> data
           (map (fn [[x y]]
                   (fm/sqr
                    (- y (f x)))))
           (reduce +)))))

(comment



  (defn ^GradientProblem linear-problem
    [factors target]
    (GradientProblem. (objective-function
                       (fn [& point]
                         (->> (matrix/e* factors point)
                              (map (fn [target-i value]
                                     (- value target-i))
                                   target)
                              (reduce +))))

                      (objective-function-gradient
                       (fn [& point]
                         (let [r (ops/- (matrix/e* factors point)
                                        target)]
                           (matrix/e* (matrix/transpose target)
                                      r
                                      2))))))


  (defn least-squares-problem
    "Least squares problem: https://en.wikipedia.org/wiki/Linear_least_squares_(mathematics)#The_general_problem"
    [points]
    (let [factors (->> points (map butlast) (map #(cons 1 %)))
          target  (map last points)]
      (GradientProblem. (objective-function
                         (fn [intercept slope]
                           (let [f   (line intercept slope)
                                 res (->> points
                                          (map (fn [[x y]]
                                                 (fm/sqr (- y (f x)))))
                                          (reduce +))]
                             res)))

                        (objective-function-gradient
                         (fn [& point]
                           ;; M = (X ^ T * X)
                           ;; b = (X ^ T * y)
                           ;; beta = M^-1 * b
                           (let [ft (matrix/transpose factors)
                                 m! (matrix/inverse (matrix/dot ft factors))
                                 b  (matrix/dot ft target)]

                             (ops/- (matrix/mmul m! b)
                                    point)))))))


  (defn two-var-least-squares
    [points]
    (GradientProblem. (objective-function
                       (fn [intercept slope]
                         (let [f   (line intercept slope)
                               res (->> points
                                        (map (fn [[x y]]
                                               (fm/sqr (- y (f x)))))
                                        (reduce +))]
                           res)))

                      (objective-function-gradient
                       (fn [intercept slope]
                         [ (* 2 (->> points
                                     (map (fn [[x y]]
                                            (*
                                             (- y (+ intercept (* slope x)))
                                             -1)))
                                     (reduce +)))

                           (* 2 (->> points
                                     (map (fn [[x y]]
                                            (*
                                             (- y (+ intercept (* slope x)))
                                             -1
                                             x)))
                                     (reduce +)))

                           ]))))
  )
