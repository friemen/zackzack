(ns zackzack.demo.addressbook-test
  (:require [clojure.test :refer [deftest testing is are use-fixtures run-tests]]
            [zackzack.webdriver :as browser :refer [click! get! enabled? set-value! close!]]
            [zackzack.backend :as server]))


(defn setup
  [f]
  (server/start!)
  (Thread/sleep 100)
  (binding [browser/*driver* (browser/firefox-driver)]
    (f)
    (close!))
  (server/stop!))


(use-fixtures :once setup)


(deftest addressbook-test
  (get! "http://localhost:8080/index.html")
  (click! "addressbook")
  (click! "private")
  (is (not (enabled? "add")) "add button is disabled")
  (set-value! "company" "doctronic")
  (click! "add"))
