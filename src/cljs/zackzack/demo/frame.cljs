(ns zackzack.demo.frame
  (:require [cljs.core.async :refer [put! chan]]
            [zackzack.elements :refer [frame togglelink]]
            [zackzack.demo.addressbook :refer [addressbook-view]]))


(def frame-ch (chan))


(defn switch-view
  [state {:keys [id] :as event}]
  (let [{:keys [view-model view-id]} (-> state :active)]
    (if (not= view-id id)
      (-> state
          (assoc-in [:active] (case id
                                "addressbook" {:view-model addressbook-view :view-id id}))
          (assoc-in [:links (keyword view-id) :active] false)
          (assoc-in [:links (keyword id) :active] true))
      state)))


(def frame-view
  {:spec (frame "frame"
                :path nil
                :links [(togglelink "addressbook")
                        (togglelink "gallery")])
   :ch frame-ch
   :actions {:addressbook switch-view}})

