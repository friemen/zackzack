(defproject zackzack "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :min-lein-version "2.3.4"
  
  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj" "test/cljs"]
  
  :dependencies [[org.clojure/clojure "1.6.0"]
                 ;; cljs deps
                 [org.clojure/clojurescript "0.0-2311"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [om "0.7.1"]
                 [cljs-ajax "0.3.3"]
                 ;; clj deps
                 [ring "1.3.1"]
                 [ring-transit "0.1.2"]
                 [http-kit "2.1.19"]
                 [compojure "1.2.1"]]

  
  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]
            [com.cemerick/clojurescript.test "0.3.1"]]

  :hooks [leiningen.cljsbuild]

  :cljsbuild {:builds {:zackzack
                       {:source-paths ["src/cljs"]
                        :compiler
                        {:output-to "resources/public/js/zackzack.min.js"
                         :output-dir "resources/public/js"
                         :externs ["resources/public/jslib/moment-2.8.3.min.js"
                                   "resources/public/jslib/pikaday-1.3.0.min.js"
                                   "resources/public/jslib/react-0.11.1.min.js"]
                         :optimizations :advanced
                         :pretty-print false}}}}
  
  :profiles
  {:dev {:clean-targets ["out" :target-path]
         :source-paths ["dev-resources/tools/http" "dev-resources/tools/repl"]
         :test-paths ["test/clj" "test/cljs"]
         :resource-paths ["resources"]
         :dependencies [[ring "1.3.1"]
                        [compojure "1.2.1"]
                        [enlive "1.1.5"]]
         :plugins [[com.cemerick/austin "0.1.5"]]
        
         :injections [(require '[ring.server :as http :refer [run]]
                               'cemerick.austin.repls)
                      (defn browser-repl []
                        (cemerick.austin.repls/cljs-repl (reset! cemerick.austin.repls/browser-repl-env
                                                                 (cemerick.austin/repl-env))))]
         :cljsbuild {:builds {:zackzack
                              {:source-paths ["src/cljs" "test/cljs" "dev-resources/tools/repl"]
                               :compiler
                               {:output-to "resources/public/js/zackzack.js"
                                :output-dir "resources/public/js"
                                :source-map "resources/public/js/zackzack.js.map"
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
                                :optimizations :none
                                :pretty-print false}}}}}})
