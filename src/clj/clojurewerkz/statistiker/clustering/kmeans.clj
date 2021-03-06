(ns clojurewerkz.statistiker.clustering.kmeans
  (:import [org.apache.commons.math3.ml.clustering KMeansPlusPlusClusterer])

  (:require [clojurewerkz.statistiker.utils    :as u]
            [clojurewerkz.statistiker.distance :as distance]))

(defn- ^KMeansPlusPlusClusterer clusterer
  ([k max-iter]
     (KMeansPlusPlusClusterer. k max-iter))
  ([k max-iter distance-measure]
     (KMeansPlusPlusClusterer. k max-iter (get distance/distance-measure-instances distance-measure))))

(defn cluster
  [initial k max-iter]
  (let [clusterer (clusterer k max-iter)]
    (->> initial
         (map u/double-point)
         (.cluster clusterer)
         (map #(hash-map :center (vec (.getPoint (.getCenter %)))
                         :points (map (fn [a] (with-meta (vec (.getPoint a))
                                               (.getMetadata a)))
                                      (.getPoints %)))))))

(defn cluster-by
  "Clusters the hashmaps by fields. Fields should be given as vector. Field values
   should be numerical.

   Resulting hashmap will be returned with :cluster-id field that identifies the cluster"
  ([data fields k]
     (cluster-by data fields k 100))
  ([data fields k iterations]
     (let [vectors (u/prepare-vectors fields data)
           clusters (map :points (cluster vectors k iterations))]
       (u/unmeta-clusters clusters))))
