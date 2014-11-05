(ns zackzack.backend
  (:require [org.httpkit.server :as httpkit]
            [compojure.handler :as handler]
            [ring.util.response :refer [redirect response]]
            [hiccup.page :refer [html5]]
            [hiccup.form :as f]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]))

;;-------------------------------------------------------------------
;; default data

(def addresses (atom {1 {:id 1 :name "Mini" :street "Foobar"}
                      2 {:id 2 :name "Donald" :street "Barbaz"}}))


;;-------------------------------------------------------------------
;; routing

(defroutes app
  (GET "/" [] (redirect "index.html"))
  (route/resources "/")
  (route/not-found "Not found"))


;; -------------------------------------------------------------------
;; http server start/stop infrastructure

(defonce http-server (atom nil))

(defn stop!
  "Stops the http server if started."
  []
  (when-let [shutdown-fn @http-server]
    (shutdown-fn)
    (reset! http-server nil)
    :stopped))


(defn start!
  "Starts http server, which is reachable on http://localhost:8080"
  []
  (stop!)
  (reset! http-server (httpkit/run-server (handler/site #'app) {:port 8080}))
  :started) 

