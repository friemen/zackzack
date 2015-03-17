(defproject zackzack "0.0.1-SNAPSHOT"
  :description "Om / core.async frontend prototype"
  :url "https://github.com/friemen/zackzack"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :min-lein-version "2.5.0"
  
  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj" "test/cljs"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 ;; cljs deps
                 [org.clojure/clojurescript "0.0-3123" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.omcljs/om "0.8.8"]
                 [cljs-http "0.1.27"]
                 [examine "1.2.0"]
                 [weasel "0.6.0"]
                 ;; clj deps
                 [org.clojure/tools.nrepl "0.2.8"]
                 [com.cemerick/piggieback "0.1.6-SNAPSHOT"]
                 [ring "1.3.2"]
                 [ring-transit "0.1.3"]
                 [http-kit "2.1.19"]
                 [compojure "1.3.2"]]

  :main zackzack.backend
  :uberjar-name "zackzack.jar"
  :ring {:handler zackzack.backend/app
         :port 8080}
  
  :plugins [[lein-ring "0.8.13"]
            [lein-cljsbuild "1.0.5"]
            [com.cemerick/clojurescript.test "0.3.1"]]

  :hooks [leiningen.cljsbuild]

  :cljsbuild {:builds {:zackzack
                       {:source-paths ["src/cljs"]
                        :compiler
                        {:output-to "resources/public/js/zackzack.min.js"
                         :output-dir "resources/public/js"
                         :externs ["resources/public/jslib/moment-2.8.3.min.js"
                                   "resources/public/jslib/pikaday-1.3.0.min.js"]
                         :optimizations :advanced
                         :pretty-print false}}}}
  
  :profiles
  {:dev {:clean-targets ["out" :target-path]
         :test-paths ["test/clj" "test/cljs"]
         :resource-paths ["resources"]
         :repl-options {:init-ns zackzack.backend
                        :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
         :cljsbuild {:builds {:zackzack
                              {:source-paths ["src/cljs" "test/cljs"]
                               :compiler
                               {:output-to "resources/public/js/zackzack.js"
                                :output-dir "resources/public/js"
                                :source-map "resources/public/js/zackzack.js.map"
                                :source-map-path "js/zackzack.js.map"
                                :optimizations :whitespace
                                :pretty-print true}}}
                     :test-commands {"phantomjs"
                                     ["phantomjs" :runner "resources/public/js/zackzack.js"]}}}
   
   :auto {:cljsbuild {:builds {:zackzack
                              {:source-paths ["src/cljs" "test/cljs"]
                               :compiler
                               {:output-to "resources/public/js/zackzack.js"
                                :output-dir "resources/public/js"
                                :source-map "resources/public/js/zackzack.js.map"
                                :source-map-path "js/zackzack.js.map"
                                :optimizations :none
                                :pretty-print false}}}}}
   :uberjar {:omit-source true
             :aot :all}})
