(require '[clojure.edn :as edn])

(defproject webhook-explorer "0.1.0-SNAPSHOT"
  :description "Webhook Explorer"
  :url "http://www.webhook-explorer.com"
  :plugins [[s3-wagon-private "1.1.2"]
            [lein-cljsbuild "1.1.7"]]
  :dependencies [[reagent "0.8.1"]
                 [cljs-http "0.1.46"]
                 [clj-commons/secretary "1.2.4"]]
  :source-paths ["src"]
  :profiles {:dev
              {:dependencies [[org.clojure/clojurescript "1.10.516"]
                              [com.bhauman/figwheel-main "0.2.0"]]}}
  :resource-paths ["target"]
  :figwheel {:hawk-options {:watcher :polling}}
  :cljsbuild {:builds [{:source-paths ["src"]
                        :compiler {:main webhook-explorer.core
                                   :npm-deps {amazon-cognito-auth-js "1.3.2"
                                              react "16.8.6"
                                              react-dom "16.8.6"}
                                   :install-deps true}}]}
  :repl-options {:init-ns webhook-explorer.core})
