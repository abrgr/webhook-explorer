(require '[clojure.edn :as edn])

(defproject webhook-explorer "0.1.0-SNAPSHOT"
  :description "Webhook Explorer"
  :url "http://www.webhook-explorer.com"
  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.18"]]
  :dependencies [[reagent "0.8.1"]
                 [cljs-http "0.1.46"]
                 [clj-commons/secretary "1.2.4"]]
  :source-paths ["src"]
  :profiles {:dev
              {:dependencies [[org.clojure/clojurescript "1.10.516"]
                              [com.bhauman/figwheel-main "0.2.0"]]}}
  :figwheel {:server-port 9500
             :server-logfile "tmp/figwheel.log"
             :http-server-root "public"
             :hawk-options {:watcher :polling}}
  :cljsbuild {:builds [{:id :dev
                        :source-paths ["src"]
                        :compiler {:main webhook-explorer.core
                                   :asset-path "js/out"
                                   :output-to "resources/public/js/dev-main.js"
                                   :npm-deps {amazon-cognito-auth-js "1.3.2"
                                              react "16.8.6"
                                              react-dom "16.8.6"}
                                   :install-deps true
                                   :closure-defines {"global" "window"}}}]}
  :repl-options {:init-ns webhook-explorer.core})
