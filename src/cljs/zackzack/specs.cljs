(ns zackzack.specs
  "Factory functions used to form a specification"
  (:require [clojure.string :as str]))


(defn- spec
  [type id path]
  {:type type
   :id id
   :path path})


(defn bar
  [id & {:keys [path links] :or {path [(keyword id)]}}]
  (assoc (spec ::bar id path)
    :links links))


(defn button
  [id & {:keys [path text] :or {path [(keyword id)]
                                text (str/capitalize id)}}]
  (assoc (spec ::button id path)
    :text text))


(defn checkbox
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (spec ::checkbox id path)
    :label label))


(defn column
  [id & {:keys [getter title] :or {title (str/capitalize id)
                                   getter (keyword id)}}]
  {:type ::column :id id :getter getter :title title})


(defn datepicker
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (spec ::datepicker id path)
    :label label))


(defn togglelink
  [id & {:keys [path text] :or {path [(keyword id)]
                                text (str/capitalize id)}}]
  (assoc (spec ::togglelink id path)
    :text text))


(defn panel
  [id & {:keys [path title elements] :or {path [(keyword id)]
                                          title (str/capitalize id)}}]
  (assoc (spec ::panel id path) 
    :title title
    :elements elements))


(defn selectbox
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (spec ::selectbox id path)
    :label label))


(defn table
  [id & {:keys [path label columns] :or {path [(keyword id)]
                                         label (str/capitalize id)}}]
  (assoc (spec ::table id path)
    :label label
    :columns columns))


(defn textfield
  [id & {:keys [path label] :or {path [(keyword id)]
                                 label (str/capitalize id)}}]
  (assoc (spec ::textfield id path)
    :label label))


(defn view
  [id & {:keys [path spec-fn rules actions ch] :or {path [(keyword id)]
                                                    spec-fn (constantly [])
                                                    rules identity}}]
  (assoc (spec ::view id path)
    :spec-fn spec-fn
    :rules rules
    :actions actions
    :ch ch))