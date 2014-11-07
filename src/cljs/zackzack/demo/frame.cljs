(ns zackzack.demo.frame
  (:require [cljs.core.async :refer [put! chan]]
            [zackzack.specs :refer [frame togglelink]]
            [zackzack.demo.addressbook :refer [addressbook-view]]))


(def frame-ch (chan))


(defn switch-view
  [state {:keys [id] :as event}]
  (let [{:keys [view-model view-id]} (-> state :active)]
    (if (not= view-id id)
      (-> state
          (assoc-in [:active] id)
          (assoc-in [:links (keyword view-id) :active] false)
          (assoc-in [:links (keyword id) :active] true))
      state)))



(defn frame-view
  []
  {:spec-fn
   (fn [state]
     (frame "frame"
            :path nil
            :links [(togglelink "addressbook")
                    (togglelink "gallery")]
            :view-factory (case (:active state)
                            "addressbook" addressbook-view
                            nil)))
   :ch frame-ch
   :actions {:addressbook switch-view}})

