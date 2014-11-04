(ns zackzack.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [clojure.string :as str]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]))

;; This is work in progress. But comments are welcome.
;; Running code is hosted here: http://www.falkoriemenschneider.de/om-example/

;; I prefer a strict separation between presentation logic and markup
;; (the view). The Om examples I'm aware of tend to mix both aspects
;; a bit, however, IMO using channels as proposed by @swannodette is
;; the key to strong decoupling.

;; I'm interested in boring enterprise style forms-over-data UIs.  I
;; like to be able to succinctly specify the content of components
;; without mixing it up with presentation logic. However, things like
;; state, actions, validation, rules, inter-component and remote
;; communication need their place. I prefer to implement these using
;; ordinary language features like atoms and pure functions, perhaps
;; augmented with access to channels, if necessary.


;; Here are some points that I have to make up my mind about:
;;
;; Which rules determine the process+channel topology?
;;
;; By using render functions I come to ask myself if everything needs
;; to be a React component. Is just the uniformity beneficial enough?
;;
;; How can input focus be controlled?  The decision, where to take the
;; focus to, could be part of an action or, more general, in an
;; event-handler. React basically supports this:
;; http://facebook.github.io/react/docs/working-with-the-browser.html


;; Here are my decisions that explain why the code looks the way it
;; does:

;; State is kept in a global atom, according to Om's pgm model.

;; There is a generic form component that starts a generic CSP-style
;; controller process and renders a model describing the components
;; UI.

;; Rendering as well as the controller process is in general separated
;; from the Om component definition.

;; Each form component has one channel that takes every event
;; submitted by JS event listeners. Any user input is routed as
;; :update event through this channel and the application state is
;; updated immediately with the result of a field specific parser
;; function [string -> anything] application.

;; After processing an :update, validation with respect to the
;; updated field is applied. This results in a :message value
;; stored in the fields map (not yet implemented).

;; Actions (the stuff that happens for example upon button clicks) are
;; triggered by :action events and should ideally be pure functions of
;; the form [state event -> state]. If they communicate with a server
;; or another component they would either use their own or a foreign
;; components channel, respectively.  In case of accessing channels
;; action functions are no longer pure, so should be named with a !
;; suffix.

;; After processing of an :update or :action event a rules function
;; [state -> state] is called that ensures that invariants are
;; re-established.

;; To enable communication among controller processes the channels are
;; globally defined before the actions. The channels are passed as
;; part of the component model to the controller and to render
;; functions. An action that wishes to send an event to a different
;; process would use type :action, which, in turn, triggers the execution
;; of an arbitrary action function.


;; TODO
;; Validation
;; Formatting / parsing of values
;; Async invocation of services
;; Inter-component communication
;; Controlling input focus

;; If you read the code below, please note that the first part is
;; reusable, technical stuff. The second part is the implementation
;; of specific "business" functionality.


;; ----------------------------------------------------------------------------
;; Utilities

(def log (.-log js/console))
(enable-console-print!)


(defn as-vector
  [x]
  (cond
   (vector? x) x
   (coll? x) (vec x)
   :else [x]))


(defn get-all
  [state k ks]
  (reduce (fn [m k']
            (assoc m k' (get-in state (conj (as-vector k') k))) )
          {}
          ks))


(defn update-all
  [state k ks values]
  (reduce (fn [m k']
            (assoc-in m [k' k] (get values k')))
          state
          ks))


(defn remove-selected
  [state path]
  (let [sel-path   (conj path :selection)
        items-path (conj path :items)
        selected   (get-in state sel-path)]
    (-> state
        (update-in items-path
                   #(vec (keep-indexed (fn [index item]
                                         (when-not (selected index)
                                           item)) %)))
        (assoc-in sel-path #{}))))


(defn add-or-replace
  [v index values]
  (if index
    (assoc v index values)
    (conj v values)))


;; ----------------------------------------------------------------------------
;; HTML rendering functions

(defn wrap
  ([f xs]
     (wrap f nil xs))
  ([f js-map xs]
     (apply (partial f js-map) xs)))


(defn with-label
  ([label content]
     (with-label label nil content))
  ([label message content]
     (dom/div #js {:className "def-labeledwidget"}
              (if label
                (dom/label nil
                           (dom/span #js {:className "def-label"} label)
                           content)
                content)
              (if message (dom/span #js {:className "error-message"} message)))))


(defn warn-if-state-missing
  [element state]
  (when-not state
    (log (str "No state defined for path " (:path element)))))


(defn render
  [element ch state]
  ((:renderer element) element ch (get-in state (:path element))))


(defn render-table
  [{:keys [id label columns] :as element} ch {:keys [items visible selection] :as state}]
  (warn-if-state-missing element state)
  (letfn [(render-row [index item selected?]
            (wrap dom/tr #js {:className (and selected? "selected")
                              :onClick #(put! ch {:type :update
                                                  :id id
                                                  :state state
                                                  :key :selection
                                                  :value #{index}})}
                  (for [c columns]
                    (dom/td nil ((:getter c) item)))))]
    (cond
     (not visible)  (dom/div nil)
     (empty? items) (with-label label (dom/p nil "No items to display."))  
     :else          (if (seq items)
                      (with-label label 
                        (dom/table #js {:className "def-table"}
                                   (dom/thead nil
                                              (wrap dom/tr
                                                    (for [c columns]
                                                      (dom/th nil (:title c)))))
                                   (wrap dom/tbody
                                         (for [[i item] (map vector (range) items)]
                                           (render-row i item (get selection i))))))))))


(defn render-panel
  [{:keys [title elements]} ch {:keys [title] :or {title title} :as state}]
  (wrap dom/div #js {:className "def-panel"}
        (cons (dom/h1 #js {:className "def-paneltitle"} title)
              (for [e elements]
                (render e ch state)))))



(defn render-button
  [{:keys [id text]} ch {:keys [disabled] :as state}]
  (dom/input #js {:type "button"
                  :className "def-button"
                  :value (or (:text state) text)
                  :id id
                  :disabled disabled
                  :onClick #(put! ch {:type :action
                                      :id id})}))


(defn update!
  [ch id state evt]
  (put! ch {:type :update
            :id id
            :state state
            :key :value
            :value (-> evt .-target .-value)}))


(defn render-textfield
  [{:keys [id label] :as element} ch {:keys [value message] :as state}]
  (warn-if-state-missing element state)
  (let [update-fn (partial update! ch id state)]
    (with-label label message
      (dom/input #js {:className "def-field"
                      :type "text"
                      :value (or value "")
                      :id id
                      :ref (name id)
                      :onBlur update-fn
                      :onChange update-fn}))))


(defn render-selectbox
  [{:keys [id label] :as element} ch {:keys [value message items] :as state}]
  (warn-if-state-missing element state)
  (with-label label message
    (wrap dom/select #js {:className "def-field"
                          :value (or value "")
                          :id id
                          :ref (name id)
                          :onChange (partial update! ch id state)}
          (for [i items]
            (dom/option (clj->js i) (:value i))))))


(defn render-component
  [component-fn element ch state]
  (om/build component-fn state {:opts {:model element
                                       :ch ch}}))


;; ----------------------------------------------------------------------------
;; A generic controller and component for forms


(defn validator
  [state path value]
  (let [message-path (conj path :message)
        validate-fn #(if (empty? %) "Must not be empty.")] ;TODO this is a dummy
    (assoc-in state message-path (validate-fn value))))


(defn controller
  [state {:keys [actions ch rules] :or {rules identity}}]
  (go-loop []
    (let [{:keys [type id] :as event} (<! ch)]
      (prn type id)
      (case type
        :init
        (om/transact! state rules)
        :update 
        (let [path (-> event :state om/path)]
          (om/transact! state #(let [{:keys [state key value parser] :or {parser identity}} event
                                     parsed-value (parser value)]
                                 (-> %
                                     (assoc-in (conj path key) parsed-value)
                                     (validator path parsed-value)
                                     (rules)))))
        :action
        (when-let [action (actions (keyword id))]
          (om/transact! state #(-> %
                                   (action event)
                                   (rules)))))
      (recur))))


(defn form-component
  [state owner {:keys [model]}]
  (let [{:keys [view ch]} model]
    (reify
      om/IWillMount
      (will-mount [_]
        (controller state model))
      om/IDidMount
      (did-mount [_]
        (put! ch {:type :init :id (:id view)}))
      om/IRender
      (render [_]
        (render view ch state)))))


;; ----------------------------------------------------------------------------
;; Other components

(defn datepicker-component
  [state owner {:keys [model ch]}]
  (reify
    om/IInitState
    (init-state [_]
      {:ch ch})
    om/IDidMount
    (did-mount [_]
      (let [input (om.core/get-node owner (-> model :id name))]
        (js/Pikaday. #js {:field input
                          :format "DD.MM.YYYY"})))
    om/IRenderState
    (render-state [_ {:keys [ch]}]
      (render-textfield model ch state))))


;; ----------------------------------------------------------------------------
;; Factory functions used to form a model

(defn element
  [renderer-fn id path]
  {:renderer renderer-fn
   :id id
   :path path})


(defn column
  [id & {:keys [getter title] :or {title (str/capitalize id)
                                   getter (keyword id)}}]
  {:id id :getter getter :title title})


(defn table
  [id & {:keys [path label columns] :or {path [(keyword id)]
                                         label (str/capitalize id)}}]
  (assoc (element render-table id path)
    :label label
    :columns columns))


(defn button
  [id & {:keys [path text] :or {path [(keyword id)]
                                text (str/capitalize id)}}]
  (assoc (element render-button id path)
    :text text))


(defn textfield
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (element render-textfield id path)
    :label label))


(defn datepicker
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (element (partial render-component datepicker-component)
                  id path)
    :label label))


(defn selectbox
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (element render-selectbox id path)
    :label label))


(defn panel
  [id & {:keys [path title elements] :or {path [(keyword id)]
                                          title (str/capitalize id)}}]
  {:renderer render-panel
   :id id
   :path path 
   :title title
   :elements elements})


;; ============================================================================
;; The code above is reusable code, the code below is specific to
;; a particular "business" functionality.


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
                                             (button "add" :text "Add Address")])
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
             :delete delete-addresses}
   :rules addressbook-rules})


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

(om/root form-component
         state-ref
         {:target (. js/document (getElementById "app"))
          :opts {:model addressbook-view}})
