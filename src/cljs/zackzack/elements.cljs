(ns zackzack.elements
  "Factory functions used to form a model"
  (:require [zackzack.components :as z]
            [clojure.string :as str]))


(defn- element
  [renderer-fn id path]
  {:renderer renderer-fn
   :id id
   :path path})


(defn button
  [id & {:keys [path text] :or {path [(keyword id)]
                                text (str/capitalize id)}}]
  (assoc (element z/render-button id path)
    :text text))


(defn column
  [id & {:keys [getter title] :or {title (str/capitalize id)
                                   getter (keyword id)}}]
  {:id id :getter getter :title title})


(defn datepicker
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (element (partial z/render-component z/datepicker-component)
                  id path)
    :label label))


(defn frame
  [id & {:keys [path links] :or {path [(keyword id)]}}]
  (assoc (element z/render-frame id path)
    :links links))


(defn togglelink
  [id & {:keys [path text] :or {path [(keyword id)]
                                text (str/capitalize id)}}]
  (assoc (element z/render-togglelink id path)
    :text text))


(defn panel
  [id & {:keys [path title elements] :or {path [(keyword id)]
                                          title (str/capitalize id)}}]
  (assoc (element z/render-panel id path) 
    :title title
    :elements elements))


(defn selectbox
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (element z/render-selectbox id path)
    :label label))


(defn table
  [id & {:keys [path label columns] :or {path [(keyword id)]
                                         label (str/capitalize id)}}]
  (assoc (element z/render-table id path)
    :label label
    :columns columns))


(defn textfield
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (element z/render-textfield id path)
    :label label))


