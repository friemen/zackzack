(ns zackzack.elements
  "Factory functions used to form a model"
  (:require [clojure.string :as str]))


(defn- element
  [type id path]
  {:type type
   :id id
   :path path})


(defn button
  [id & {:keys [path text] :or {path [(keyword id)]
                                text (str/capitalize id)}}]
  (assoc (element ::button id path)
    :text text))


(defn checkbox
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (element ::checkbox id path)
    :label label))


(defn column
  [id & {:keys [getter title] :or {title (str/capitalize id)
                                   getter (keyword id)}}]
  {:type ::column :id id :getter getter :title title})


(defn datepicker
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (element ::datepicker id path)
    :label label))


(defn frame
  [id & {:keys [path links view-factory] :or {path [(keyword id)]}}]
  (assoc (element ::frame id path)
    :links links
    :view-factory view-factory))


(defn togglelink
  [id & {:keys [path text] :or {path [(keyword id)]
                                text (str/capitalize id)}}]
  (assoc (element ::togglelink id path)
    :text text))


(defn panel
  [id & {:keys [path title elements] :or {path [(keyword id)]
                                          title (str/capitalize id)}}]
  (assoc (element ::panel id path) 
    :title title
    :elements elements))


(defn selectbox
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (element ::selectbox id path)
    :label label))


(defn table
  [id & {:keys [path label columns] :or {path [(keyword id)]
                                         label (str/capitalize id)}}]
  (assoc (element ::table id path)
    :label label
    :columns columns))


(defn textfield
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (element ::textfield id path)
    :label label))


