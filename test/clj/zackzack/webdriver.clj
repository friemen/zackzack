(ns zackzack.webdriver
  (:import [org.openqa.selenium By WebDriver WebElement]
           [org.openqa.selenium.firefox FirefoxDriver]))


(def ^:dynamic *driver*)

(defn firefox-driver
  []
  (FirefoxDriver.))


(defn get!
  [url]
  (.get *driver* url))

(defn close!
  []
  (.close *driver*))

(defn element-by-id
  [id]
  (.findElement *driver* (By/id id)))


(defn click!
  [id]
  (.click (element-by-id id)))

(defn send-keys!
  [id text]
  (let [chsa (into-array java.lang.CharSequence [text])]
    (.sendKeys (element-by-id id) chsa)))


(defn clear-value!
  [id]
  (.clear (element-by-id id)))


(defn set-value!
  [id new-text]
  (clear-value! id)
  (send-keys! id new-text))


(defn attribute
  [id attr-key]
  (.getAttribute (element-by-id id) (name attr-key)))


(defn value
  [id]
  (attribute id :value))


(defn enabled?
  [id]
  (.isEnabled (element-by-id id)))


