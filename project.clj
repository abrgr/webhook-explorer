(defproject webhook-explorer "0.1.0-SNAPSHOT"
  :description "Webhook Explorer"
  :url "http://www.webhook-explorer.com"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.520"]
                 [thheller/shadow-cljs "2.8.61"]
                 [com.google.javascript/closure-compiler-unshaded "v20190325"]
                 [org.clojure/google-closure-library "0.0-20190213-2033d5d9"]
                 [reagent "0.8.1" :exclusions [cljsjs/react cljsjs/react-dom]]
                 [clj-commons/secretary "1.2.4"]
                 [cljs-http "0.1.46"]
                 [camel-snake-kebab "0.4.1"]
                 [org.clojure/data.json "0.2.7"]]
  :plugins [[lein-cljfmt "0.6.7"]]
  :source-paths ["src"]
  :profiles {:dev
              {:dependencies [[lilactown/punk-adapter-web "0.0.10"]
                              [binaryage/devtools "1.0.0"]
                              [philoskim/debux "0.6.5"]]}
             :release
              {:dependencies [[philoskim/debux-stubs "0.6.5"]]}}
  :repl-options {:init-ns webhook-explorer.core})
