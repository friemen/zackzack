(ns zackzack.backend
  (:require [clojure.java.io :as io]
            [org.httpkit.server :as httpkit]
            [compojure.handler :as handler]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.util.response :refer [redirect response]]
            [ring.middleware.transit :refer [wrap-transit-response wrap-transit-body]]
            [weasel.repl.websocket]
            [cemerick.piggieback])
  (:gen-class :main true))


(defn cljs-repl
  []
  (cemerick.piggieback/cljs-repl :repl-env (weasel.repl.websocket/repl-env)))

;;-------------------------------------------------------------------
;; default data

(def initial-addresses [{:id 1 :name "Mini" :street "Downstreet" :city "Duckberg" :birthday "01.01.1950"}
                        {:id 2 :name "Donald" :street "Upperstreet" :city "Duckberg" :birthday "01.01.1955"}])


(def addresses (atom (->> initial-addresses
                          (map (juxt :id identity))
                          (into {}))))

;;-------------------------------------------------------------------
;; routing

(defroutes routes
  (GET "/" [] (redirect "index.html"))
  (GET "/addresses" [] {:status 200 :body (-> @addresses vals vec)})
  (route/resources "/")
  (route/not-found "Not found"))


(def app (-> #'routes
             wrap-transit-response
             wrap-transit-body
             handler/site))

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
          (httpkit/run-server app {:port 8080}))
  :started) 


(defn -main
  [& args]
  (start!))
