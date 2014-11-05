(ns zackzack.demo.app
  (:require [om.core :as om :include-macros true]
            [zackzack.components :as z]
            [zackzack.demo.addressbook :refer [addressbook-view]]))




;; ----------------------------------------------------------------------------
;; Initial setup

(def state-ref
  (atom {:edit-index nil
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
                             {:name "Donald" :street "Upperstreet" :city "Duckberg" :birthday "01.01.1955"}]}}))

(defn refresh
  []
  (om/root z/form-component
           state-ref
           {:target (. js/document (getElementById "app"))
            :opts {:model addressbook-view}}))

(refresh)
