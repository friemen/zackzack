(ns zackzack.demo.playground
  (:require [examine.constraints :as c]
            [examine.core :as e]
            [clojure.string :as string]
            [zackzack.specs :refer [action-link button checkbox column
                                    datepicker panel
                                    selectbox table textfield view]])
  (:require-macros [examine.macros :refer [defvalidator]]
                   [cljs.core.async.macros :refer [go]]))


(defn convert-text
  [state event]
  (update-in state [:firstname :value] string/upper-case))


(defn playground-view
  []
  (view "playground"
        :elements [(textfield "firstname" :label "First name")
                   (textfield "lastname" :label "Last name")
                   (button "convert")]
        :actions {:convert convert-text}))
