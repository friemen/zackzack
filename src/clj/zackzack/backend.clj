(ns zackzack.backend
  (:require [org.httpkit.server :as httpkit]
            [compojure.handler :as handler]
            [ring.util.response :refer [redirect response]]
            [hiccup.page :refer [html5]]
            [hiccup.form :as f]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.transit :refer [wrap-transit-response wrap-transit-body]]))

;;-------------------------------------------------------------------
;; default data

(def initial-addresses [{:id 1 :name "Mini" :street "Downstreet" :city "Duckberg" :birthday "01.01.1950"}
                        {:id 2 :name "Donald" :street "Upperstreet" :city "Duckberg" :birthday "01.01.1955"}])


(def addresses (atom (->> initial-addresses
                          (map (juxt :id identity))
                          (into {}))))



;;-------------------------------------------------------------------
;; routing

(defroutes app
  (GET "/" [] (redirect "index.html"))
  (GET "/addresses" [] {:status 200 :body (-> @addresses vals vec)})
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
  (reset! http-server
          (httpkit/run-server (-> #'app
                                  wrap-transit-response
                                  wrap-transit-body
                                  handler/site)
                              {:port 8080}))
  :started) 

