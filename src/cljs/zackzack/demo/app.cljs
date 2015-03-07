(ns zackzack.demo.app
  "Umbrella to start Om application"
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom]
            [cljs.core.async :refer [chan]]
            [weasel.repl :as repl]
            [zackzack.components :as z]
            [zackzack.demo.frame :refer [frame-view]]
            [zackzack.demo.addressbook :refer [addressbook-view]]))


(defn browser-connect
  "Connects as client to a piggiebacked Cljs REPL.
  Called by resources/public/testindex.html."
  []
  (if-not (repl/alive?)
    (repl/connect "ws://localhost:9001")))



;; ----------------------------------------------------------------------------
;; Initial setup

(def state-ref
  (atom  {:active nil
          :confirm {:active false
                    :text "Message"}
          :message {:text nil}
          :bar {:links {:addressbook
                        {:disabled false
                         :active false}
                        :gallery
                        {:disabled false
                         :active false}}}
          :playground {:firstname {:value ""}
                       :lastname {:value ""}}
          :gallery {:text {:value ""}
                    :select  {:value "One"
                              :items [{:value "One"}
                                      {:value "Two"}]}}
          :addressbook {:details {:edit-index nil
                                  :private {:value true}
                                  :company  {:value ""}
                                  :name {:value "foo"}
                                  :street {:value "bar"}
                                  :city {:value "Bonn"
                                         :items [{:value nil :name "none"}
                                                 {:value "Bonn" :name "bonn"}
                                                 {:value "Cologne" :name "cologne"}
                                                 {:value "Duckberg" :name "duckberg"}]}
                                  :birthday {:value ""}}
                        :addresses {:visible true
                                    :selection #{0}
                                    :items [{:private true
                                             :name "Mini"
                                             :street "Downstreet"
                                             :city "Duckberg"
                                             :birthday "01.01.1950"}
                                            {:private false
                                             :name "Donald"
                                             :street "Upperstreet"
                                             :city "Duckberg"
                                             :birthday "01.01.1955"}]}}}))



(defn refresh
  []
  (om/root z/app
           state-ref
           {:target (. js/document (getElementById "app"))
            :opts {:spec (frame-view)}}))

(refresh)


