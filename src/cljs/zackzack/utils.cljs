(ns zackzack.utils
  "Misc utilities")


(defn log [& xs]
  (.log js/console (apply pr-str xs)))

(enable-console-print!)


(defn as-vector
  [x]
  (cond
   (vector? x) x
   (coll? x) (vec x)
   :else [x]))


(defn get-all
  [state k ks]
  (reduce (fn [m k']
            (assoc m k' (get-in state (conj (as-vector k') k))) )
          {}
          ks))


(defn update-all
  [state k ks values]
  (reduce (fn [m k']
            (assoc-in m [k' k] (get values k')))
          state
          ks))


(defn remove-selected
  [state path]
  (let [sel-path   (conj path :selection)
        items-path (conj path :items)
        selected   (get-in state sel-path)]
    (-> state
        (update-in items-path
                   #(vec (keep-indexed (fn [index item]
                                         (when-not (selected index)
                                           item)) %)))
        (assoc-in sel-path #{}))))


(defn add-or-replace
  [v index values]
  (if index
    (assoc v index values)
    (conj v values)))

