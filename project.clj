(defproject dankreek/lik-m-aid "0.0.1"
  :description "A 2d canvas sprite library for ClojureScript"
  :url "http://github.com/dankreek/lik-m-aid"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repositories [["releases" {:url "https://clojars.org/repo"
                              :sign-releases false
                              :username :env/CLOJARS_USERNAME
                              :password :env/CLOJARS_PASSWORD}]]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.660"]
                 [cljsjs/pixi "4.5.4-0"]
                 [dankreek/rektify "0.0.1"]]

  :plugins [[lein-figwheel "0.5.11"]
            [lein-cljsbuild "1.1.6" :exclusions [[org.clojure/clojure]]]]

  ;; Only need source in the output jar, everything in resources is for the examples
  :jar-exclusions [#"public/.*"]

  :source-paths ["src"]

  :test-paths ["test"]

  :clean-targets ^{:protect false} [:target-path
                                    "resources/public/cljs"]

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src" "dev/cljs"]

                :figwheel {:open-urls ["http://localhost:3449/repl.html"]}

                :compiler {:main lik-m-aid.user
                           :optimizations :none
                           :asset-path "cljs/compiled/out"
                           :output-dir "resources/public/cljs/compiled/out"
                           :output-to "resources/public/cljs/compiled/lik-m-aid.js"
                           :source-map-timestamp true
                           :parallel-build true
                           :preloads [devtools.preload]}}

               {:id "test"
                :source-paths ["src" "test"]
                :compiler {:main runners.doo
                           :asset-path "/js/out"
                           :output-to "target/test.js"
                           :output-dir "target/cljstest/public/js/out"
                           :optimizations :whitespace
                           :libs ["test/js/test.classes.js"]}}

               {:id "devcards"
                :source-paths ["src" "dev/cljs"
                               "test/lik_m_aid"
                               "test/runners/browser.cljs"
                               "test/runners/tests.cljs"]
                :figwheel {:devcards true
                           :open-urls ["http://localhost:3449/tests.html"]}
                :compiler {:main runners.browser
                           :optimizations :none
                           :asset-path "cljs/tests/out"
                           :output-dir "resources/public/cljs/tests/out"
                           :output-to "resources/public/cljs/tests/all-tests.js"
                           :source-map-timestamp true
                           :parallel-build true
                           :preloads [devtools.preload]}}]}

  :figwheel {:css-dirs  ["resources/public/css"]}

  :profiles {:dev {:dependencies [[binaryage/devtools "0.9.4"]
                                  [figwheel-sidecar "0.5.11"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [devcards "0.2.3"]
                                  [org.clojure/core.async "0.3.443"]
                                  ]

                   :source-paths ["src" "dev/clj" "dev/cljs"]

                   :plugins [[lein-doo "0.1.6" :exclusions [[org.clojure/clojure]]]
                             ]

                   :repl-options {; for nREPL dev you really need to limit output
                                  :init (set! *print-length* 50)
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   }
             }

  :aliases {"test" ["doo" "phantom" "test" "once"]
            "test-chrome" ["doo" "chrome" "test"]
            "test-phantom" ["doo" "phantom" "test"]
            "dev-test" ["test-phantom"]}
)
