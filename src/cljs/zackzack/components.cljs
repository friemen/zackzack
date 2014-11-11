(ns zackzack.components
  "Om components"
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]
            [zackzack.utils :refer [log]]
            [zackzack.specs :as sp]))



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
  [spec state]
  (when-not state
    (log (str "No state defined for path " (:path spec)))))


(defn- update!
  [ch id state evt]
  (put! ch {:type :update
            :id id
            :state state
            :key :value
            :value (-> evt .-target .-value)}))


(defn- action!
  [ch id spec evt]
  (put! ch {:type :action
            :id id
            :spec spec}))


;; ----------------------------------------------------------------------------
;; Components without own controller

(declare view)
(declare build)


(defn bar
  [{:keys [active] :as state} _ {{:keys [links]} :spec ch :ch}]
  (om/component
   (dom/div nil
            (wrap dom/div #js {:className "def-header"}
                  (for [l links]
                    (build l ch (:links state)))))))


(defn button
  [{:keys [disabled] :as state} _ {{:keys [id text] :as spec} :spec ch :ch}]
  (om/component
   (dom/input #js {:id id
                   :className "def-button"
                   :type "button"
                   :value (or (:text state) text)
                   :disabled disabled
                   :onClick (partial action! ch id spec)})))


(defn checkbox
  [{:keys [disabled value] :as state} _ {{:keys [id label]} :spec ch :ch}]
  (om/component
   (with-label label nil
     (dom/input #js {:id id
                     :className "def-checkbox"
                     :type "checkbox"
                     :disabled disabled
                     :checked value
                     :onChange #(put! ch {:type :update
                                          :id id
                                          :state state
                                          :key :value
                                          :value (-> % .-target .-checked)})}))))


(defn datepicker
  [state owner {:keys [spec ch]}]
  (let [update-fn (partial update! ch (:id spec) state)]
    (reify
      om/IInitState
      (init-state [_]
        {:ch ch})
      om/IDidMount
      (did-mount [_]
        (let [input (om.core/get-node owner (-> spec :id name))]
          (js/Pikaday. #js {:field input
                            :format "DD.MM.YYYY"})))
      om/IRenderState
      (render-state [_ {:keys [ch]}]
        (let [{:keys [message disabled value]} state
              {:keys [label id]} spec]
            (with-label label message
              (dom/input #js {:className "def-field"
                              :type "text"
                              :value (or value "")
                              :disabled disabled
                              :id id
                              :ref (name id)
                              :onBlur update-fn
                              :onChange update-fn})))))))


(defn panel
  [{:keys [title] :as state} _ {{:keys [title elements] :or {title title}} :spec ch :ch}]
  (om/component
   (wrap dom/div #js {:className "def-panel"}
         (cons (dom/h1 #js {:className "def-paneltitle"} title)
               (for [e elements]
                 (build e ch state))))))



(defn selectbox
  [{:keys [value message items] :as state} _ {{:keys [id label] :as spec} :spec ch :ch}]
  (warn-if-state-missing spec state)
  (om/component
   (with-label label message
     (wrap dom/select #js {:id id
                           :className "def-field"
                           :value (or value "")
                           :ref (name id)
                           :onChange (partial update! ch id state)}
           (for [i items]
             (dom/option (clj->js i) (:value i)))))))


(defn table
  [{:keys [items visible selection] :as state} _ {{:keys [id label columns] :as spec} :spec ch :ch}]
  (warn-if-state-missing spec state)
  (om/component
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
                                            (render-row i item (get selection i)))))))))))


(defn togglelink
  [{:keys [active disabled] :as state} _ {{:keys [id text] :as spec} :spec ch :ch}]
  (om/component
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
                          :onClick (partial action! ch id spec)}
                     text)))))


(defn textfield
  [{:keys [value message disabled] :as state} _ {{:keys [id label] :as spec} :spec ch :ch}]
  (warn-if-state-missing spec state)
  (let [update-fn (partial update! ch id state)]
    (om/component
     (with-label label message
       (dom/input #js {:className "def-field"
                       :type "text"
                       :value (or value "")
                       :disabled disabled
                       :id id
                       :ref (name id)
                       :onBlur update-fn
                       :onChange update-fn})))))


;; ----------------------------------------------------------------------------
;; Keep all view channels in a global map

(def channels (atom {}))


(defn put-view!
  [view-id message]
  (if-let [ch (get @channels view-id)]
    (put! ch message)
    (log (str "WARNING: No channel for '" view-id "' found"))))


;; ----------------------------------------------------------------------------
;; A generic controller and component for views


(defn validator
  [state path value]
  (let [message-path (conj path :message)
        ;TODO this is a dummy:
        validate-fn #(if (and (string? %) (empty? %)) "Please enter a value.")]
    (assoc-in state message-path (validate-fn value))))


(defn controller
  [state {:keys [actions parent-ch ch rules] :or {rules identity} :as spec}]
  (go-loop []
    (let [{:keys [type id] :as event} (<! ch)]
      (log (:id spec) type id)
      (case type
        :init
        (om/transact! state rules)
        :update 
        (let [path (->> event :state om/path
                        (drop (-> state om/path count))
                        (vec))]
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


(defn view
  [state owner {:keys [spec view-factory ch]}]
  (reify
    om/IInitState
    (init-state [_]
      (assoc (or spec (view-factory))
        :ch (chan)
        :parent-ch ch))
    om/IWillMount
    (will-mount [_]
      (let [{:keys [id ch] :as spec} (om/get-state owner [])]
        (swap! channels assoc id ch)
        (log (str "Channels: " (clojure.string/join "," (keys @channels))))
        (controller state spec)))
    om/IWillUnmount
    (will-unmount [_]
      (swap! channels dissoc (:id (om/get-state owner []))))
    om/IDidMount
    (did-mount [_]
      (let [{:keys [id ch]} (om/get-state owner [])]
        (put! ch {:type :init :id id})))
    om/IRenderState
    (render-state [_ {:keys [id ch spec-fn]}]
      (dom/div nil (build (sp/panel id
                                    :path nil
                                    :title nil
                                    :elements (spec-fn state))
                          ch state)))))


;; ----------------------------------------------------------------------------
;; Generic builder

(defn build
  [spec ch state]
  (when spec
    (let [f (case (:type spec)
              ::sp/bar        bar
              ::sp/button     button
              ::sp/checkbox   checkbox
              ::sp/datepicker datepicker
              ::sp/panel      panel
              ::sp/selectbox  selectbox
              ::sp/table      table
              ::sp/togglelink togglelink
              ::sp/textfield  textfield
              ::sp/view       view)]
      #_(prn "build" (:type spec) (and state (om/path state)) (:path spec))
      (om/build f (get-in state (:path spec)) {:opts {:spec spec :ch ch}}))))

