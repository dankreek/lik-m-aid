(ns devcards.basics
  (:require-macros
    [devcards.core :as dc :refer [defcard dom-node]])
  (:require [lik-m-aid.core :as lma]
            [goog.dom :as dom]))

(defonce application (lma/create-system))
(defonce *tick (atom 0.0))

(def bob-url "img/bob.png")

(def width 640)
(def height 360)

(def speed 0.01)
(def magnitude 4)

;; TODO: *** User reagent to make the render function and all application properties selectable within a menu of some kind
;; TODO: *** make different figwheel builds for tests and this devcard. Too much shit happening at once.

(defn render
  [frame-delta]
  (let [tick (swap! *tick + frame-delta)]
    (lma/container {}
      (lma/sprite {:texture :bob
                   :x (/ width 2)
                   :y (/ height 2)
                   :anchor 0.5
                   :scale (-> tick
                              (* speed)
                              Math/sin
                              (* magnitude))}))))

(defn -main [node]
  (lma/update-sys-config! application
                          {:background-color 0xff99bb
                               :dimensions [width height]}
                          {:bob bob-url})
  (lma/render application node render))

(defcard hello-bob
  "
  # Markdown Docs
  Soon you'll be able to select a demo.
  "
  (dom-node (fn [_ node] (-main node))))

;; TODO: Put config in the application constructor
;; TODO: put resources and callbacks in the config map
