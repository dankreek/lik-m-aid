(ns lik-m-aid.user
  (:require [clojure.string :as str]
            [lik-m-aid.core :as lma]
            [goog.dom :as dom]))

(enable-console-print!)

(def bob-url "img/bob.png")
(def ninja-url "img/little_ninja.json")

(def width 640)
(def height 360)

;; TODO: Figure out how to put this in the API
(set! (.. js/PIXI -settings -SCALE_MODE) js/PIXI.SCALE_MODES.NEAREST)
(defonce app (lma/create-system))
(defonce frame (atom 0))

(def ninja-frames [[:ninja "1.png"]
                   [:ninja "2.png"]
                   [:ninja "3.png"]
                   [:ninja "4.png"]])

(defn render
  [time-delta]
  ;; XXX: Create generators here
  (let [frame (swap! frame + time-delta)]
    (lma/container {}
      (lma/sprite {:texture :bob
                   :anchor 0.5
                   :scale 10
                   :rotation (/ Math/PI 2)
                   :x (/ width 2)
                   :y (/ height 2)})

      (lma/animated-sprite {:textures ninja-frames
                            :x (-> (* frame 1.50) (mod width))
                            :y (/ height 2)
                            :animation-speed 0.095
                            :scale [-18 18]
                            :anchor 0.5}))))

(defn -main []
  (lma/update-sys-config! app {:background-color 0x102030
                               :dimensions [width height]}
                          {:ninja ninja-url
                           :bob bob-url})
  (lma/render app (dom/getElement "application") render))

(-main)
