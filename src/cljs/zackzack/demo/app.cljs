(ns zackzack.demo.app
  "Umbrella to start Om application"
  (:require [om.core :as om :include-macros true]
            [cljs.core.async :refer [chan]]
            [zackzack.components :as z]
            [zackzack.demo.frame :refer [frame-view]]
            [zackzack.demo.addressbook :refer [addressbook-view]]))




;; ----------------------------------------------------------------------------
;; Initial setup

(def state-ref
  (atom  {:active nil
          :links {:addressbook {:disabled false
                                :active false}
                  :gallery {:disabled true
                           :active false}}
          :addressbook {:edit-index nil
                        :details {:name {:value "foo"}
                                  :street {:value "bar"}
                                  :city {:value "Cologne"
                                         :items [{:value nil :name "none"}
                                                 {:value "Bonn" :name "bonn"}
                                                 {:value "Cologne" :name "cologne"}
                                                 {:value "Duckberg" :name "duckberg"}]}
                                  :birthday {:value ""}}
                        :addresses {:visible true
                                    :selection #{0}
                                    :items [{:name "Mini" :street "Downstreet" :city "Duckberg" :birthday "01.01.1950"}
                                            {:name "Donald" :street "Upperstreet" :city "Duckberg" :birthday "01.01.1955"}]}}}))


(defn refresh
  []
  (om/root z/view-component
           state-ref
           {:target (. js/document (getElementById "app"))
            :opts {:model frame-view}}))

(refresh)
