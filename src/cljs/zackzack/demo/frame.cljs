(ns zackzack.demo.frame
  (:require [cljs.core.async :refer [put! chan]]
            [zackzack.specs :refer [bar view togglelink]]
            [zackzack.demo.addressbook :refer [addressbook-view]]
            [zackzack.demo.gallery :refer [gallery-view]]))


(defn switch-view
  [state {:keys [id] :as event}]
  (let [view-id (-> state :active)]
    (if (not= view-id id)
      (-> state
          (assoc-in [:active] id)
          (assoc-in [:bar :links (keyword view-id) :active] false)
          (assoc-in [:bar :links (keyword id) :active] true))
      state)))



(defn frame-view
  []
  (view "frame"
        :path nil
        :title nil
        :spec-fn
        (fn [state]
          (let [active-view-id (-> state :active)]            
            [(bar "bar" :links [(togglelink "addressbook")
                                (togglelink "gallery")])
             (case active-view-id
               "addressbook" (addressbook-view "addressbook")
               "gallery"     (gallery-view)
               nil)]))
        :actions {:addressbook switch-view
                  :gallery     switch-view}))

