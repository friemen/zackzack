(ns zackzack.components
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]
            [zackzack.utils :refer [log]]))



;; ----------------------------------------------------------------------------
;; Internal rendering utils

(defn- wrap
  ([f xs]
     (wrap f nil xs))
  ([f js-map xs]
     (apply (partial f js-map) xs)))


(defn- with-label
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


(defn- warn-if-state-missing
  [element state]
  (when-not state
    (log (str "No state defined for path " (:path element)))))


(defn- update!
  [ch id state evt]
  (put! ch {:type :update
            :id id
            :state state
            :key :value
            :value (-> evt .-target .-value)}))


(defn- action!
  [ch id element evt]
  (put! ch {:type :action
            :id id
            :element element}))


;; ----------------------------------------------------------------------------
;; HTML rendering functions

(declare view-component)

(defn render
  [element ch state]
  ((:renderer element) element ch (get-in state (:path element))))


(defn render-button
  [{:keys [id text] :as element} ch {:keys [disabled] :as state}]
  (dom/input #js {:id id
                  :className "def-button"
                  :type "button"
                  :value (or (:text state) text)
                  :disabled disabled
                  :onClick (partial action! ch id element)}))


(defn render-component
  [component-fn element ch state]
  (om/build component-fn state {:opts {:model element :ch ch}}))


(defn render-frame
  [{:keys [links]} ch {:keys [active] :as state}]
  (dom/div nil
           (wrap dom/div #js {:className "def-header"}
                 (for [l links]
                   (render l ch (:links state))))
           (if active
             (om/build view-component
                       state
                       {:opts {:model (om/value (:view-model active))}})
             (dom/span nil "No active view."))))


(defn render-panel
  [{:keys [title elements]} ch {:keys [title] :or {title title} :as state}]
  (wrap dom/div #js {:className "def-panel"}
        (cons (dom/h1 #js {:className "def-paneltitle"} title)
              (for [e elements]
                (render e ch state)))))



(defn render-selectbox
  [{:keys [id label] :as element} ch {:keys [value message items] :as state}]
  (warn-if-state-missing element state)
  (with-label label message
    (wrap dom/select #js {:id id
                          :className "def-field"
                          :value (or value "")
                          :ref (name id)
                          :onChange (partial update! ch id state)}
          (for [i items]
            (dom/option (clj->js i) (:value i))))))


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


(defn render-togglelink
  [{:keys [id text] :as element} ch {:keys [active disabled] :as state}]
  (cond
   disabled
   (dom/span #js {:id id
                  :className "def-togglelink-span def-togglelink-span-disabled"} text)
   active
   (dom/span #js {:id id
                  :className "def-togglelink-span def-togglelink-span-active"} text)
   :else
   (dom/span #js {:id id
                  :className "def-togglelink-span"}
             (dom/a #js {:id id
                         :className "def-togglelink"
                         :href "#"
                         :onClick (partial action! ch id element)}
                    text))))


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



;; ----------------------------------------------------------------------------
;; A generic controller and component for forms


(defn validator
  [state path value]
  (let [message-path (conj path :message)
        validate-fn #(if (empty? %) "Please enter a value.")] ;TODO this is a dummy
    (assoc-in state message-path (validate-fn value))))


(defn controller
  [state {:keys [spec actions ch rules] :or {rules identity}}]
  (go-loop []
    (let [{:keys [type id] :as event} (<! ch)]
      (log (:id spec) type id)
      (case type
        :init
        (om/transact! state rules)
        :update 
        (let [path (->> event :state om/path (drop (-> spec :path count)) vec)]
          ;; TODO the way the path is calculated is incorrect, this is only a hack
          #_(prn path)
          (om/transact! state #(let [{:keys [state key value parser] :or {parser identity}} event
                                     parsed-value (parser value)]
                                 (-> %
                                     (assoc-in (conj path key) parsed-value)
                                     (validator path parsed-value)
                                     (rules)))))
        :action
        (when-let [action (get actions (keyword id))]
          (om/transact! state
                        #(-> %
                             (action event)
                             (rules)))))
      (recur))))


(defn view-component
  [state owner {:keys [model]}]
  (let [{:keys [spec ch]} model]
    (reify
      om/IWillMount
      (will-mount [_]
        (controller (get-in state (-> spec :path)) model))
      om/IDidMount
      (did-mount [_]
        (put! ch {:type :init :id (:id spec)}))
      om/IRender
      (render [_]
        (render spec ch state)))))


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



