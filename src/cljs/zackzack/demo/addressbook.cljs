(ns zackzack.demo.addressbook
  (:require [cljs.core.async :refer [put! chan]]
            [zackzack.utils :refer [get-all update-all remove-selected add-or-replace]]
            [zackzack.elements :refer [button column datepicker panel
                                       selectbox table textfield]]))


;; Component channels
;; ----------------------------------------------------------------------------

(def addressbook-ch (chan))


;; ----------------------------------------------------------------------------
;; Actions are functions [state event -> state]

(def fields [:name :street :city :birthday])

(defn add-address
  [state event]
  (let [a  (get-all (:details state) :value fields)
        i  (-> state :edit-index)]
    (-> state
        (update-in [:addresses :items] add-or-replace i a)
        (assoc-in  [:edit-index] nil)
        (update-in [:details] update-all :value fields nil))))


(defn edit-address
  [state event]
  (if-let [i (first (get-in state [:addresses :selection]))]    
    (let [a (get-in state [:addresses :items i])]
      (-> state
          (assoc-in  [:edit-index] i)
          (update-in [:details] update-all :value fields a)
          (update-in [:details] update-all :message fields nil)))
    state))


(defn reset-address
  [state event]
  (-> state
      (assoc-in  [:edit-index] nil)
      (update-in [:details] update-all :value fields nil)
      (update-in [:details] update-all :message fields nil)))


(defn delete-addresses
  [state event]
  (remove-selected state [:addresses]))


;; ----------------------------------------------------------------------------
;; Rules are represented by a sole function [state -> state]


(defn addressbook-rules
  [state]
  (let [none-sel?  (-> state :addresses :selection empty?)
        invalid?   (->> fields (get-all (:details state) :value) (vals) (some empty?)) ; TODO validation
        edit?      (-> state :edit-index)]
    (-> state
        (assoc-in [:details :add :text]     (if edit? "Update"))
        (assoc-in [:details :title]         (if edit? "Edit Details" "Details"))
        (assoc-in [:edit :disabled]         none-sel?)
        (assoc-in [:delete :disabled]       none-sel?)
        (assoc-in [:details :add :disabled] (if invalid? true)))))


;; ----------------------------------------------------------------------------
;; A concise "model" of the Addressbook UI


(def addressbook-view
  {:view (panel "addressbook"
                :path nil
                :title "Addressbook"
                :elements [(panel "details"
                                  :title "Details"
                                  :elements [(textfield "name" :label "Full name")
                                             (textfield "street")
                                             (selectbox "city")
                                             (datepicker "birthday")
                                             (button "add" :text "Add Address") (button "reset")])
                           (table "addresses"
                                  :label "Addresses"
                                  :columns [(column "name")
                                            (column "street")
                                            (column "city")
                                            (column "birthday")])
                           (button "edit") (button "delete")])
   :ch addressbook-ch
   :actions {:add    add-address
             :edit   edit-address
             :delete delete-addresses
             :reset  reset-address}
   :rules addressbook-rules})

