(ns zackzack.demo.addressbook
  (:require [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]
            [examine.constraints :as c]
            [examine.core :as e]
            [zackzack.components :refer [put-view! <ask Message]]
            [zackzack.utils :refer [get-all update-all remove-selected add-or-replace]]
            [zackzack.specs :refer [action-link button checkbox column
                                    datepicker panel
                                    selectbox table textfield view]])
  (:require-macros [examine.macros :refer [defvalidator]]
                   [cljs.core.async.macros :refer [go go-loop]]))



;; ============================================================================
;; Address Details


(def fields [:private :name :company :street :city :birthday])


(def address-constraints
  (e/rule-set
   
   [[:name :value]]
   c/required (c/min-length 3)
   
   [[:private :value] [:company :value]]
   (fn [p? c]
     (if p?
       (if (> (count c) 0)
         "Company must be empty")
       (if (empty? c)
         "Company must not be empty")))))


;; ----------------------------------------------------------------------------
;; Actions are functions [state event -> state]

(defn details-add!
  [state event]
  (let [a  (get-all state :value fields)
        i  (-> state :edit-index)]
    (put-view! "addressbook" {:type :action
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
;; All rules are represented by one function [state -> state]

(defn addressdetails-rules
  [state]
  (let [invalid?  (->> state (e/validate address-constraints) (e/has-errors?))
        edit?     (-> state :edit-index)
        private?  (-> state :private :value)]
    (-> state
        (assoc-in [:add :text]         (if edit? "Update"))
        (assoc-in [:title]             (if edit? "Edit Details" "Details"))
        (assoc-in [:company :disabled] private?)
        (update-in [:company :value]   #(if private? "" %))
        (assoc-in [:add :disabled]     (if invalid? true)))))



;; ----------------------------------------------------------------------------
;; A concise "model" of the Addressbook view

(defn addressdetails-view
  []
  (view "details"
        :elements
        [(panel "fields" :layout :two-columns
                :elements [(checkbox "private")
                           (textfield "name" :label "Full name")
                           (textfield "company")
                           (textfield "street")
                           (selectbox "city")
                           (datepicker "birthday")])
         (panel "actions" :elements
                [(button "add" :text "Add Address") (button "reset")])]
        :actions {:add     details-add!
                  :edit    details-edit
                  :reset   details-reset}
        :rules addressdetails-rules
        :constraints address-constraints))



;; ============================================================================
;; Addressbook


;; ----------------------------------------------------------------------------
;; Actions are functions [state event -> state]

(defn addressbook-add
  [state {:keys [address index]}]
  (update-in state [:addresses :items] add-or-replace index address))


(defn addressbook-edit!
  [state event]
  (when-let [i (first (get-in state [:addresses :selection]))]
    (let [a (get-in state [:addresses :items i])]
      (put-view! "details" {:type :action
                            :id "edit"
                            :address a
                            :index i})))
  state)


(defn <addressbook-delete
  [state event]
  (let [question (str "You're about to delete item "
                      (-> state :addresses :selection first)
                      ". Are you sure?")]
    (go (if (= :ok (<! (<ask question)))
          (remove-selected state [:addresses])
          state))))


(defn <addressbook-reload
  [state event]
  (go (if (= :ok (<! (<ask "You will loose all your local changes. Are you sure?")))
        (let [{s :status addresses :body} (<! (http/get "/addresses"))]
          (if (= 200 s)
            (assoc-in state [:addresses :items] addresses)
            (Message. "Error loading addresses")))
        state)))


;; ----------------------------------------------------------------------------
;; Rules are represented by one function [state -> state]

(defn addressbook-rules
  [state]
  (let [none-sel?  (-> state :addresses :selection empty?)]
    (-> state
        (assoc-in [:edit :disabled]   none-sel?)
        (assoc-in [:delete :disabled] none-sel?))))



;; ----------------------------------------------------------------------------
;; A concise "model" of the Addressbook view

(defn addressbook-view
  [id]
  (view id
        :elements
        [(addressdetails-view)
         (table "addresses"
                :columns [(column "name")
                          (column "company")
                          (column "street")
                          (column "city")
                          (column "birthday")]
                :actions-fn (fn [item]
                              [(action-link "edit" :image "images/pencil.png")
                               (action-link "delete" :image "images/cross.png")]))
         (button "reload")]
        :actions {:add       addressbook-add
                  :edit      addressbook-edit!
                  :delete    <addressbook-delete
                  :reload    <addressbook-reload}
        :rules addressbook-rules))

