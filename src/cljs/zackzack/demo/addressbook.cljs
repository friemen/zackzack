(ns zackzack.demo.addressbook
  (:require [cljs.core.async :refer [put! chan]]
            [ajax.core :refer [GET POST]]
            [zackzack.utils :refer [get-all update-all remove-selected add-or-replace]]
            [zackzack.elements :refer [button column datepicker panel
                                       selectbox table textfield]]))



;; Component channel
;; ----------------------------------------------------------------------------

(def addressbook-ch (chan))


;; Remote access
;; ----------------------------------------------------------------------------

(defn load-addresses
  []
  (GET "/addresses" {:handler #(put! addressbook-ch {:type :action
                                                     :id "addresses"
                                                     :payload %})}))

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


(defn reload-addresses
  [state event]
  (load-addresses)
  state)


(defn replace-addresses
  [state {:keys [payload]}]
  (-> state
      (assoc-in [:addresses :items] payload)))


;; ----------------------------------------------------------------------------
;; Rules are represented by a sole function [state -> state]


(defn addressbook-rules
  [state]
  (let [none-sel?  (-> state :addresses :selection empty?)
        ;; TODO real validation
        invalid?   (->> fields (get-all (:details state) :value) (vals) (some empty?))
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
  {:spec (panel "addressbook"
                :elements [(panel "details"
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
                           (button "edit") (button "delete") (button "reload")])
   :ch addressbook-ch
   :actions {:add       add-address
             :edit      edit-address
             :delete    delete-addresses
             :reset     reset-address
             :reload    reload-addresses
             :addresses replace-addresses}
   :rules addressbook-rules})

