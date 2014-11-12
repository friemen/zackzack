(ns zackzack.demo.gallery
  (:require [zackzack.specs :refer [button checkbox column datepicker panel
                                    selectbox table textfield separator view]]))

(defn gallery-view
  []
  (view "gallery"
        :elements
        [(separator "button")
         (button "press me")
         (separator "textfield")
         (textfield "text")
         (separator "selectbox")
         (selectbox "select")]))
