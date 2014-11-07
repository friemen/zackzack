(ns zackzack.demo.frame
  (:require [cljs.core.async :refer [put! chan]]
            [zackzack.specs :refer [bar view togglelink]]
            [zackzack.demo.addressbook :refer [addressbook-view]]))


(def frame-ch (chan))


(defn switch-view
  [state {:keys [id] :as event}]
  (let [{:keys [view-model view-id]} (-> state :bar :active)]
    (if (not= view-id id)
      (-> state
          (assoc-in [:bar :active] id)
          (assoc-in [:bar :links (keyword view-id) :active] false)
          (assoc-in [:bar :links (keyword id) :active] true))
      state)))



(defn frame-view
  []
  (view "frame"
        :path nil
        :spec-fn
        (fn [state]
          [(bar "bar" :links [(togglelink "addressbook")
                              (togglelink "gallery")])
           (case (-> state :bar :active)
             "addressbook" (addressbook-view)
             nil)])
        :ch frame-ch
        :actions {:addressbook switch-view}))

