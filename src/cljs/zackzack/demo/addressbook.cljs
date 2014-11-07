(ns zackzack.demo.addressbook
  (:require [cljs.core.async :refer [put! chan]]
            [ajax.core :refer [GET POST]]
            [zackzack.utils :refer [get-all update-all remove-selected add-or-replace]]
            [zackzack.specs :refer [button checkbox column datepicker panel
                                    selectbox table textfield view]]))


;; View channels
;; ----------------------------------------------------------------------------

(def addressbook-ch (chan))
(def addressdetails-ch (chan))


;; Remote access
;; ----------------------------------------------------------------------------

(defn load-addresses
  []
  (GET "/addresses" {:handler #(put! addressbook-ch {:type :action
                                                     :id "addresses"
                                                     :payload %})}))


;; ============================================================================
;; Address Details


(def fields [:private :name :company :street :city :birthday])


;; ----------------------------------------------------------------------------
;; Actions are functions [state event -> state]

(defn details-add!
  [state event]
  (let [a  (get-all state :value fields)
        i  (-> state :edit-index)]
    (put! addressbook-ch {:type :action
                          :id "add"
                          :address a
                          :index i})
    (-> state
        (assoc-in  [:edit-index] nil)
        (update-all :value fields nil))))


(defn details-reset
  [state event]
  (-> state
      (assoc-in  [:edit-index] nil)
      (update-all :value fields nil)
      (update-all :message fields nil)))


(defn details-edit
  [state {:keys [address index]}]
  (-> state
          (assoc-in  [:edit-index] index)
          (update-all :value fields address)
          (update-all :message fields nil)))


;; ----------------------------------------------------------------------------
;; Rules are represented by a sole function [state -> state]

(defn addressdetails-rules
  [state]
  (let [;; TODO use real validation
        invalid?   (->> fields
                        (get-all state :value)
                        (filter #(-> % first #{:name :street :city :birthday}))
                        (map second)
                        (some empty?))
        edit?      (-> state :edit-index)
        private?   (-> state :private :value)]
    (-> state
        (assoc-in [:add :text]         (if edit? "Update"))
        (assoc-in [:title]             (if edit? "Edit Details" "Details"))
        (assoc-in [:company :disabled] private?)
        (assoc-in [:add :disabled]     (if invalid? true)))))



;; ----------------------------------------------------------------------------
;; A concise "model" of the Addressbook view

(defn addressdetails-view
  []
  (view "details"
        :spec-fn
        (fn [state]
          [(checkbox "private")
           (textfield "name" :label "Full name")
           (textfield "company")
           (textfield "street")
           (selectbox "city")
           (datepicker "birthday")
           (button "add" :text "Add Address") (button "reset")])
        :ch addressdetails-ch
        :actions {:add       details-add
                  :edit      details-edit!
                  :reset     details-reset}
        :rules addressdetails-rules))



;; ============================================================================
;; Addressbook


;; ----------------------------------------------------------------------------
;; Actions are functions [state event -> state]

(defn addressbook-add
  [state {:keys [address index]}]
  (prn index address)
  (update-in state [:addresses :items] add-or-replace index address))


(defn addressbook-edit!
  [state event]
  (when-let [i (first (get-in state [:addresses :selection]))]    
    (let [a (get-in state [:addresses :items i])]
      (put! addressdetails-ch {:type :action
                               :id "edit"
                               :address a
                               :index i})))
  state)


(defn addressbook-delete
  [state event]
  (remove-selected state [:addresses]))


(defn addressbook-load
  [state event]
  (load-addresses)
  state)


(defn addressbook-replace
  [state {:keys [payload]}]
  (-> state
      (assoc-in [:addresses :items] payload)))


;; ----------------------------------------------------------------------------
;; Rules are represented by a sole function [state -> state]

(defn addressbook-rules
  [state]
  (let [none-sel?  (-> state :addresses :selection empty?)]
    (-> state
        (assoc-in [:edit :disabled]             none-sel?)
        (assoc-in [:delete :disabled]           none-sel?))))



;; ----------------------------------------------------------------------------
;; A concise "model" of the Addressbook view

(defn addressbook-view
  []
  (view "addressbook"
        :spec-fn
        (fn [state]
          [(addressdetails-view)
           (table "addresses"
                  :label "Addresses"
                  :columns [(column "name")
                            (column "company")
                            (column "street")
                            (column "city")
                            (column "birthday")])
           (button "edit") (button "delete") (button "reload")])
        :ch addressbook-ch
        :actions {:add       addressbook-add
                  :edit      addressbook-edit!
                  :delete    addressbook-delete
                  :reload    addressbook-reload
                  :addresses addressbook-replace}
        :rules addressbook-rules))

