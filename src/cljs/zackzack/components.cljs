(ns zackzack.components
  "Om components"
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async.impl.protocols :as asyncimpl]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]
            [examine.core :as e]
            [zackzack.utils :refer [log as-vector]]
            [zackzack.specs :as sp]))



;; ----------------------------------------------------------------------------
;; Internal rendering utils

(defn- wrap
  ([f xs]
     (wrap f nil xs))
  ([f js-map xs]
     (apply (partial f js-map) xs)))


(defn- with-label
  [label content]
  (dom/div #js {:className "def-labeledwidget"}
           (if label
             (dom/label nil
                        (dom/span #js {:className "def-label"} label)
                        content)
             content)))


(defn- with-message
  [message position content]
  (if message
    (let [class (if (= position :below)
                   "error-message-below"
                   "error-message-beneath")]
      (dom/span nil
                content
                (dom/span #js {:className class} message)))
    content))


(defn- warn-if-state-missing
  [spec cursor]
  (when-not cursor
    (log (str "No cursor defined for path " (:path spec)))))


(defn- update!
  [ch id cursor evt]
  (put! ch {:type :update
            :id id
            :cursor cursor
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
(declare glass)


(defn app [cursor owner {:keys [spec]}]
  (om/component
   (dom/div nil
            (build spec nil cursor)
            (om/build glass (:glass cursor)))))


(defn bar
  [{:keys [active] :as cursor} _ {{:keys [links]} :spec ch :ch}]
  (om/component
   (dom/div nil
            (wrap dom/div #js {:className "def-header"}
                  (conj (vec (for [l links]
                               (build l ch (:links cursor))))
                        (dom/div #js {:className "def-togglelink-span"}))))))


(defn button
  [{:keys [disabled] :as cursor} _ {{:keys [id text] :as spec} :spec ch :ch}]
  (om/component
   (dom/input #js {:id id
                   :className "def-button"
                   :type "button"
                   :value (or (:text cursor) text)
                   :disabled disabled
                   :onClick (partial action! ch id spec)})))


(defn checkbox
  [{:keys [disabled value message] :as cursor} _ {{:keys [id label message-position]} :spec ch :ch}]
  (om/component
   (with-label label
     (with-message message :beneath
       (dom/input #js {:id id
                       :className "def-checkbox"
                       :type "checkbox"
                       :disabled disabled
                       :checked value
                       :onChange #(put! ch {:type :update
                                            :id id
                                            :cursor cursor
                                            :key :value
                                            :value (-> % .-target .-checked)})})))))


(defn datepicker
  [cursor owner {:keys [spec ch]}]
  (let [update-fn (partial update! ch (:id spec) cursor)]
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
        (let [{:keys [message disabled value]} cursor
              {:keys [id label message-position]} spec]
          (with-label label
                (with-message message message-position
                  (dom/input #js {:className "def-field"
                                  :type "text"
                                  :value (or value "")
                                  :disabled disabled
                                  :id id
                                  :ref (name id)
                                  :onBlur update-fn
                                  :onChange update-fn}))))))))


(def glass-request-ch (chan))
(def glass-response-ch (chan))

(defn glass
  [cursor owner props]
  (reify
    om/IWillMount
    (will-mount [_]
      (go-loop []
        (let [event (<! glass-request-ch)]
          (om/update! cursor {:text (:message event) :active true})
          (recur))))
    om/IRender
    (render [_]
      (if (:active cursor)
        (dom/div #js {:id "glass"
                      :className "glass"}
                 (dom/div #js {:className "glass-content"}
                          (-> cursor :text)
                          (dom/div nil
                                   (dom/input #js {:type "button"
                                                   :value "OK"
                                                   :onClick #(do (om/update! cursor [:active] false)
                                                                 (put! glass-response-ch {:type :action :value :ok}))})
                                   (dom/input #js {:type "button"
                                                   :value "Cancel"
                                                   :onClick #(do (om/update! cursor [:active] false)
                                                                 (put! glass-response-ch {:type :action :value :cancel}))}))))
        (dom/div #js {:id "glass"})))))


(defn panel
  [cursor _ {{:keys [id title layout elements]} :spec ch :ch}]
  (om/component
   (dom/div nil
            (if-let [t (if (not= title :none) (or (:title cursor) title))]
              (dom/h1 #js {:className "def-title"} t))
            (wrap dom/div #js {:id id
                               :className (case layout
                                            :two-columns "two-column-panel"
                                            "def-panel")}
                  (let [pos (if layout :below :beneath)]
                    (for [e elements :let [e' (if e (assoc e :message-position pos))]]
                      (build e' ch cursor)))))))



(defn selectbox
  [{:keys [value message items] :as cursor} _ {{:keys [id label message-position] :as spec} :spec ch :ch}]
  (warn-if-state-missing spec cursor)
  (om/component
   (with-label label
     (with-message message message-position
       (wrap dom/select #js {:id id
                             :className "def-field"
                             :value (or value "")
                             :ref (name id)
                             :onChange (partial update! ch id cursor)}
             (for [i items]
               (dom/option (clj->js i) (:value i))))))))


(defn table
  [{:keys [items visible selection] :as cursor} _ {{:keys [id label columns actions-fn] :as spec} :spec ch :ch}]
  (warn-if-state-missing spec cursor)
  (om/component
   (letfn [(update-index! [index]
             (put! ch {:type :update
                       :id id
                       :cursor cursor
                       :key :selection
                       :value #{index}}))
           (render-actions [index item]
             (wrap dom/td #js {:className "def-actionlinks"}
                   (for [{:keys [id text image]} (actions-fn item)]
                     (dom/a #js {:href "#"
                                 :className "def-actionlink"
                                 :onClick #(do
                                             (update-index! index)
                                             (action! ch id spec %)
                                             (-> % .preventDefault))}
                            (dom/img #js {:title text
                                          :src image})))))
           (render-row [index item selected?]
             (wrap dom/tr #js {:className (and selected? "selected")
                               :onClick #(update-index! index)}
                   (conj (vec (for [c columns]
                                (dom/td nil ((:getter c) item))))
                         (if actions-fn (render-actions index item)))))]
     (cond
      (not visible)  (dom/div nil)
      (empty? items) (with-label label (dom/p nil "No items to display."))  
      :else          (if (seq items)
                       (with-label label 
                         (dom/table #js {:id id
                                         :className "def-table"}
                                    (dom/thead nil
                                               (wrap dom/tr
                                                     (conj 
                                                      (vec (for [c columns]
                                                             (dom/th nil (:title c))))
                                                      (if actions-fn (dom/th nil "Actions")))))
                                    (wrap dom/tbody
                                          (for [[i item] (map vector (range) items)]
                                            (render-row i item (get selection i)))))))))))


(defn togglelink
  [{:keys [active disabled] :as cursor} _ {{:keys [id text] :as spec} :spec ch :ch}]
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
  [{:keys [value message disabled] :as cursor} _ {{:keys [id label message-position] :as spec} :spec ch :ch}]
  (warn-if-state-missing spec cursor)
  (let [update-fn (partial update! ch id cursor)]
    (om/component
     (with-label label
       (with-message message message-position
         (dom/input #js {:className "def-field"
                         :type "text"
                         :value (or value "")
                         :disabled disabled
                         :id id
                         :ref (name id)
                         :onBlur update-fn
                         :onChange update-fn}))))))


(defn separator
  [_ _ {{:keys [id text]} :spec}]
  (om/component
   (dom/div #js {:className "def-separator"}
            (dom/div #js {:className "def-title"} text))))

;; ----------------------------------------------------------------------------
;; API

(def channels (atom {})) ; keep all view channels in a global map


(defn put-view!
  [view-id message]
  (if-let [ch (get @channels view-id)]
    (put! ch message)
    (log (str "WARNING: No channel for '" view-id "' found"))))


(defn <ask
  [message]
  (go (>! glass-request-ch {:type :ask :message message})
      (:value (<! glass-response-ch))))


;; ----------------------------------------------------------------------------
;; A generic controller and component for views


(defn- attach-messages
  [state]
  (let [results (::validation-results state)]
    (reduce (fn [state path]
              (assoc-in state
                        (conj (vec (butlast path)) :message)
                        (apply str (e/messages-for path results))))
            state
            (->> results (keys) (mapcat identity)))))


(defn- validate
  [state constraints path {:keys [key] :as event}]
  (let [constraints (if path
                      (e/sub-set constraints (conj path (:key event)))
                      constraints)]
    (-> state
        (update-in [::validation-results]
                   #(e/update % (e/validate constraints state)))
        (attach-messages))))


(defn- update-state
  [state path {:keys [key value parser]:or {parser identity} :as event}]
  (if path 
    (let [message-path (conj path :message)
          value-path   (conj path key)
          [parsed ex]  (try [(parser value) nil]
                            (catch js/Error ex
                              [nil ex]))]
      (if ex
        (-> state
            (assoc-in value-path value)
            (assoc-in message-path "Invalid format"))
        (assoc-in state value-path parsed)))
    ;; an empty path means the whole state will be replaced 
    value))


(defn- exec-action
  [state f event ch]
  (let [result (f state event)]
    (if (nil? result)
      (log "Action returned nil, which is most likely a programming error")
      (if (satisfies? asyncimpl/Channel result)
        (do (go (let [result (<! result)]
                  (>! ch {:type :update
                          :id "async"
                          :key nil
                          :cursor nil
                          :value result})))
            state)
        result))))


(defn- controller
  [cursor {:keys [actions parent-ch ch rules constraints] :or {rules identity} :as spec}]
  (go-loop []
    (let [{:keys [type id] :as event} (<! ch)]
      (log (:id spec) type id)
      (case type
        :init
        (om/transact! cursor rules)
        :update 
        (let [path (some->> event :cursor om/path
                            (drop (-> cursor om/path count))
                            (vec))]
          (om/transact! cursor #(-> %
                                   (update-state path event)
                                   (rules)
                                   (validate constraints path event))))
        :action
        (if-let [action-fn (get actions (keyword id))]
          (om/transact! cursor #(-> %
                                    (exec-action action-fn event ch)
                                    (rules)))
          (log (str "WARNING: No action defined for " (keyword id)))))
      (recur))))


(defn view
  [cursor owner {:keys [spec ch]}]
  #_(prn "VIEW" (:id spec))
  (reify
    om/IInitState
    (init-state [_]
      #_(prn "INIT" (:id spec))
      {:ch (chan)
       :parent-ch ch})
    om/IWillMount
    (will-mount [_]
      #_(prn "WILLMOUNT" (:id spec))
      (swap! channels assoc (:id spec) (om/get-state owner :ch))
      (controller cursor (merge spec (om/get-state owner))))
    om/IWillUnmount
    (will-unmount [_]
      #_(prn "WILLUNMOUNT" (:id spec))
      (swap! channels dissoc (:id spec)))
    om/IDidMount
    (did-mount [_]
      #_(prn "DIDMOUNT" (:id spec))
      (put! (om/get-state owner :ch) {:type :init :id (:id spec)}))
    om/IRenderState
    (render-state [_ {:keys [ch]}]
      (let [{:keys [id title layout elements spec-fn]} spec
            es (or elements (-> cursor spec-fn as-vector))
            ch (om/get-state owner :ch)]
        #_(prn "RENDER" id (count es) layout)
        (build (sp/panel id
                         :title title
                         :layout layout
                         :elements es)
               ch cursor)))))


;; ----------------------------------------------------------------------------
;; Generic builder

(defn build
  [spec ch cursor]
  (when spec
    (let [f (case (:type spec)
              ::sp/bar        bar
              ::sp/button     button
              ::sp/checkbox   checkbox
              ::sp/datepicker datepicker
              ::sp/panel      panel
              ::sp/selectbox  selectbox
              ::sp/separator  separator
              ::sp/table      table
              ::sp/togglelink togglelink
              ::sp/textfield  textfield
              ::sp/view       view)]
      #_(prn (str "BUILD" (:type spec) " " (:id spec)  " " (and cursor (om/path cursor)) " " (:path spec)))
      (om/build f (get-in cursor (:path spec)) {:react-key (:id spec)
                                               :opts {:spec spec :ch ch}}))))

