(ns lik-m-aid.core-test
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.test :refer-macros [is testing run-tests async]]
            [cljs.core.async :as < :refer [<! >!]]
            [devcards.core :refer-macros [deftest]]
            [lik-m-aid.core :as lma]
            [clojure.zip :as z]
            [cljsjs.pixi]
            [goog.dom :as dom]
            [clojure.string :as str]
            [clojure.set :as set]
            [rektify.core :as rekt]))

(def bob-url "/img/bob.png")
(def ninja-url "/img/little_ninja.png")
(def ninja-sprite-sheet-url "/img/little_ninja.json")

;; TODO: Use this instead of messy manual array digging
(defn pixi-zip
  "Create a zipper over a tree of PixiJS objects."
  [head-object]
  (z/zipper (fn [n] (exists? (.-children n)))
            (fn [n] (seq (.-children n)))
            (fn [node children]
              (doseq [child children]
                (.addChild node child)))
            head-object))


(defn child-count
  [pixi-obj]
  (.. pixi-obj -children -length))


(def loader-props
  (let [browser-origin (.-origin js/location)]
    {:base-url (if (str/starts-with? browser-origin "file:")
                 "file:./resources/public"
                 browser-origin)}))

(def white-texture js/PIXI.Texture.WHITE)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Graph manipulation

;; TODO: Write tests to ensure graph manipulation functions all work

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Primitive object tests

;; TODO: Redo most of these tests to make them more applicable to lik-m-aid and less general to rektfy

(deftest apply-attributes-to-a-new-pixi-object
  (let [initial-props {:scale [42 69], :x 1, :y 2}
        sprite (rekt/new-object lma/sprite-config initial-props)
        next-props {:scale [420 512], :x 5, :y 6}]
    (testing "The empty texture is set if none was provided"
      (is (= lma/empty-texture (.-texture sprite))))

    (testing "Appyling attributes to Pixi object"
      (rekt/apply-props! sprite initial-props)
      (is (= 42 (.. sprite -scale -x)))
      (is (= 69 (.. sprite -scale -y)))
      (is (= 1 (.. sprite -x)))
      (is (= 2 (.. sprite -y))))

    (testing "Applying new attributes to a Pixi Object"
      (rekt/apply-props! sprite next-props)
      (is (= 420 (.. sprite -scale -x)))
      (is (= 512 (.. sprite -scale -y)))
      (is (= 5 (.. sprite -x)))
      (is (= 6 (.. sprite -y))))

    (testing "Applying default attributes to a Pixi object when an attribute is missing"
      (let [default-sprite-props (:default-props lma/sprite-config)]
        (rekt/apply-props! sprite {:x 9})
        (is (= (:scale default-sprite-props) (lma/get-point sprite "scale")))
        (is (= 9 (.. sprite -x)))
        (is (= (:y default-sprite-props) (.. sprite -y)))))))


(deftest extend-an-existing-renderer-object
  (testing "Extending an existing PIXI.Renderer"
    (let [init-props {:dimensions [100 200]
                      :background-color 0x1099bb
                      :round-pixels? true}
          new-props {:background-color 0x694242
                     :dimensions [420 42]}
          default-props (:default-props lma/renderer-config)
          renderer (.autoDetectRenderer js/PIXI #js {"width" (get-in init-props [:dimensions 0])
                                                     "height" (get-in init-props [:dimensions 1])
                                                     "backgroundColor" (:background-color init-props)
                                                     "roundPixels" (:round-pixels? init-props)})]
      (lma/extend-renderer! renderer)
      (testing "and it has the initial properties correctly set"
        (is (= (merge default-props init-props) (rekt/get-object-props renderer))
            "Initial properties are correct"))
      (testing "and it can correctly updates properties"
        (rekt/apply-props! renderer new-props)
        (is (= new-props (rekt/get-object-props renderer)))
        (is (= (get-in new-props [:dimensions 0]) (.-width renderer)))
        (is (= (get-in new-props [:dimensions 1]) (.-height renderer)))
        (is (= (:background-color new-props)) (.-backgroundColor renderer))))))

(deftest extend-an-exsting-pixi-object
  (testing "Extending an existing PIXI.Container"
    (let [container (new js/PIXI.Container)
          default-props (:default-props lma/container-config)
          init-props {:x 420, :y 69, :rotation 1.2345, :scale [42 11]}]
      (set! (.-x container) (:x init-props))
      (set! (.-y container) (:y init-props))
      (set! (.-rotation container) (:rotation init-props))
      (aset container "scale" "x" (get-in init-props [:scale 0]))
      (aset container "scale" "y" (get-in init-props [:scale 1]))
      (let [lma-container (rekt/extend-existing-obj! container lma/container-config)
            expected-props (merge default-props init-props)]
        (testing "has the initial properties correctly set"
          (is (= expected-props (rekt/get-object-props lma-container))))

        (testing "allows changes to properties"
          ;; XXX: add this test
          )))))


(deftest container
  (let [container (rekt/new-object lma/container-config {:x 1, :y 2})]
    (is (instance? js/PIXI.Container container))
    (is (= 1 (.-x container)))
    (is (= 2 (.-y container)))))


(deftest sprite
  (let [fake-sys (reify lma/ITextureRegistry
                   (-register-texture [this obj key]
                     {:texture lma/empty-texture}))]
    (binding [lma/*current-sys* fake-sys]
      (testing "A sprite is created"
        (let [sprite (rekt/new-object lma/sprite-config {:texture :texture})]
          (is (instance? js/PIXI.Sprite sprite))
          (testing "and had its texture initially set"
            (is (= lma/empty-texture (.-texture sprite))))
          (testing "and can have its texture later set"
            (lma/-set-textures sprite {:texture white-texture})
            (is (= white-texture (.-texture sprite)))))))))


(deftest tiling-sprite
  (let [fake-sys (reify lma/ITextureRegistry
                   (-register-texture [this obj key]
                     {:texture lma/empty-texture}))]
    (binding [lma/*current-sys* fake-sys]
      (testing "A sprite is created"
        (let [sprite (rekt/new-object lma/tiling-sprite-config {:texture :texture})]
          (is (instance? js/PIXI.extras.TilingSprite sprite))
          (testing "and had its texture initially set"
            (is (= lma/empty-texture (.-texture sprite))))
          (testing "and can have its texture later set"
            (lma/-set-textures sprite {:texture white-texture})
            (is (= white-texture (.-texture sprite)))))))))


(deftest animated-sprite
  (let [fake-sys (reify lma/ITextureRegistry
                   (-register-texture [this obj key])
                   (-register-textures [this obj keys]
                     {:one lma/empty-texture
                      :two lma/empty-texture
                      :three white-texture}))
        texture-keys [:one :two :three]]
    (binding [lma/*current-sys* fake-sys]
      (testing "An animated sprite is created"
        (let [sprite (rekt/new-object lma/animated-sprite-config {:textures texture-keys})]
          (is (instance? js/PIXI.extras.AnimatedSprite sprite))
          (testing "and had all of its textures initially set correctly"
            (is (= lma/empty-texture (aget sprite "textures" 0)))
            (is (= lma/empty-texture (aget sprite "textures" 1)))
            (is (= white-texture (aget sprite "textures" 2))))

          (testing "and can have its textures later set"
            (lma/-set-textures sprite {:one white-texture
                                       :two white-texture
                                       :three lma/empty-texture})
            (is (= white-texture (aget sprite "textures" 0)))
            (is (= white-texture (aget sprite "textures" 1)))
            (is (= lma/empty-texture (aget sprite "textures" 2))))

          (testing "and plays and stop according to animation speed"
            (rekt/apply-props! sprite {:textures texture-keys
                                       :animation-speed 1})
            (is (= 1 (.-animationSpeed sprite)))
            (is (= true (.-playing sprite)))
            (rekt/apply-props! sprite {:textures texture-keys
                                       :animation-speed 0})
            (is (= 0 (.-animationSpeed sprite)))
            (is (= false (.-playing sprite))
                "AnimatedSprite is not playing after animation speed set to 0")))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; System tests

(defn destroy-sys
  [sys]
  (reset! (.-*registered-textures sys) {}) ;; to suppress warnings
  (lma/update-sys-config! sys {} {} {})
  (.destroy (.. sys -app) true))

(deftest system
  (testing "Renderer properties can be set"
    (let [sys (lma/new-system)]
      (lma/update-sys-config! sys
                              {:background-color 0x123456
                               :dimensions [123 456]}
                              {})
      (is (= 0x123456 (.. sys -app -renderer -backgroundColor)))
      (is (= 123 (.. sys -app -renderer -width)))
      (is (= 456 (.. sys -app -renderer -height)))
      (destroy-sys sys)))


  (testing "Stage properties can be set"
    (let [sys (lma/new-system)]
      (lma/update-sys-config! sys {:x 320 :y 200} {})
      (is (= 320 (.. sys -app -stage -x)))
      (is (= 200 (.. sys -app -stage -y)))
      (destroy-sys sys)))


  (testing "Loader base url is set and callbacks are all called"
    (let [sys (lma/new-system)
          *called-cbs (atom [])
          mark-cb (fn [cb-key]
                    (swap! *called-cbs conj cb-key))
          on-load-resource (fn [_] (mark-cb :on-load-resource))
          on-load-progress (fn [_] (mark-cb :on-load-progress))
          on-load-start (fn [] (mark-cb :on-load-start))]
      (async done
        (lma/update-sys-config!
          sys loader-props
          {:bob bob-url}
          {:on-load-resource on-load-resource
           :on-load-progress on-load-progress
           :on-load-start on-load-start
           :on-load-complete
           (fn []
             (is (= (:base-url loader-props) (.. sys -app -loader -baseUrl)))
             (is (= [:on-load-start :on-load-progress :on-load-resource]
                    @*called-cbs)
                 "All callbacks were called in order")
             (destroy-sys sys)
             (done))})))))


(deftest system-mount-and-render-callback
  (testing "The system's canvas can be mounted and can execute a render"
    (let [sys (lma/new-system)
          ticker (.. sys -app -ticker)
          stage (.. sys -app -stage)
          mount-point (dom/createElement "div")
          cb-chan (</chan)]
      (async done
        (.add ticker
              (fn [_]
                (is (= "CANVAS" (.-tagName (dom/getFirstElementChild mount-point))))
                (is (instance? js/PIXI.Sprite (aget stage "children" 0)))
                (destroy-sys sys)
                (done))
              nil
              (dec js/PIXI.UPDATE_PRIORITY.LOW))
        (lma/render sys
                    mount-point
                    (fn [_]
                      [(lma/sprite {:x 42, :y 69})]))))))


(deftest system-registers-and-delivers-textures
  (let [sys (lma/new-system)
        *textures-set (atom {})
        fake-sprite (reify lma/IHasTextures
                      (-set-textures
                        [this textures-map]
                        (swap! *textures-set merge textures-map)))
        textures [:bob
                  [:ninja "1.png"] [:ninja "2.png"]
                  [:ninja "3.png"] [:ninja "4.png"]]
        resources {:bob bob-url, :ninja ninja-sprite-sheet-url}
        loading-while-open (</chan)
        on-load-complete #(</close! loading-while-open)]
    (lma/update-sys-config! sys loader-props resources
                            {:on-load-complete on-load-complete})
    (testing "Textures are returned as empty from registration call"
      (is (= {:bob lma/empty-texture
              [:ninja "1.png"] lma/empty-texture
              [:ninja "2.png"] lma/empty-texture
              [:ninja "3.png"] lma/empty-texture
              [:ninja "4.png"] lma/empty-texture}
             (lma/-register-textures sys fake-sprite textures))))
    (async done
      (go (<! loading-while-open)
          (testing "Textures are set once loaded"
            (let [textures-set @*textures-set]
              (is (not= lma/empty-texture (:bob textures-set)))
              (is (not= lma/empty-texture (get textures-set [:ninja "1.png"])))
              (is (not= lma/empty-texture (get textures-set [:ninja "2.png"])))
              (is (not= lma/empty-texture (get textures-set [:ninja "3.png"])))
              (is (not= lma/empty-texture (get textures-set [:ninja "4.png"])))))


          (testing "Textures are returned already loaded"
            (lma/-unregister-object sys fake-sprite)
            (reset! *textures-set {})
            (let [textures-set (lma/-register-textures sys fake-sprite textures)]
              (is (not= lma/empty-texture (:bob textures-set)))
              (is (not= lma/empty-texture (get textures-set [:ninja "1.png"])))
              (is (not= lma/empty-texture (get textures-set [:ninja "2.png"])))
              (is (not= lma/empty-texture (get textures-set [:ninja "3.png"])))
              (is (not= lma/empty-texture (get textures-set [:ninja "4.png"]))))

            (testing "And the callback was not fired"
              (is (= {} @*textures-set))))


          (testing "Textures are set to empty if unloaded"
            (reset! *textures-set {})
            (lma/update-sys-config! sys loader-props {})
            (is (= {:bob lma/empty-texture
                    [:ninja "1.png"] lma/empty-texture
                    [:ninja "2.png"] lma/empty-texture
                    [:ninja "3.png"] lma/empty-texture
                    [:ninja "4.png"] lma/empty-texture}
                   @*textures-set)))
          (done)))))

(deftest system-sets-texture-when-loaded
  (testing "Application sets a sprite's texture when loaded"
    (let [mount-point (dom/createElement "div")
          sys (lma/new-system)
          loading-while-open (</chan)
          *texture-set (atom nil)
          fake-sprite (reify lma/IHasTextures
                        (-set-textures [_ textures-map]
                          (reset! *texture-set (first (vals (textures-map))))
                          (is (= :bob (first (keys textures-map))))))
          on-load-complete #(</close! loading-while-open)
          render-cb (fn [_] [(lma/sprite {:texture :bob})])]
      (async done
        (lma/update-sys-config! sys loader-props {:bob bob-url}
                                {:on-load-complete on-load-complete})
        (lma/render sys mount-point render-cb)
        (go (<! loading-while-open)
            (is (not= lma/empty-texture @*texture-set))
            (destroy-sys sys)
            (done))))))

(deftest system-correctly-registers-textures-for-objects
  (testing "Application correctly registers and re-registers an object's texture key"
    (let [mount-point (dom/createElement "div")
          sys (lma/new-system)
          *registered-textures (.-*registered-textures sys)
          stage (.. sys -app -stage)
          render-cb-ch (</chan)
          *render-count (atom 0)
          *registry-states (atom [])
          render-cb (fn [_]
                      (let [render-count (swap! *render-count inc)]
                        (</put! render-cb-ch render-count)
                        (condp = render-count
                          1 [(lma/sprite {:texture :first-texture})]
                          2 [(lma/sprite {:texture :second-texture})]
                          [])))]
      (lma/update-sys-config! sys loader-props {:first-texture bob-url
                                                :second-texture ninja-url})
      (lma/render sys mount-point render-cb)
      (async done
        (go-loop []
          (let [count (<! render-cb-ch)
                sprite (when (> (child-count stage) 0)
                         (.getChildAt stage 0))]
            (if (<= count 3)
              (do (condp = count
                    1 (is (= {:first-texture [sprite]}
                             @*registered-textures)
                          "When an object's created its texture is registered")
                    2 (is (= {:first-texture []
                              :second-texture [sprite]}
                             @*registered-textures)
                          "When an object's texture is changed its registration is correctly moved")
                    3 (is (= {:first-texture []
                              :second-texture []}
                             @*registered-textures)
                          "When object is destroyed it's unregistered"))
                  (recur))
              (do (destroy-sys sys)
                  (done)))))))))

(deftest system-correctly-updates-a-texture-key-url
  (testing "Application correctly updates a texture's URL"
    (let [mount-point (dom/createElement "div")
          sys (lma/new-system)
          test-ch (</chan)
          *callbacks (atom [])
          *set-texture (atom nil)
          fake-sprite (reify lma/IHasTextures
                        (-set-textures [_ textures-map]
                          (is (= :a-texture (first (keys textures-map))))
                          (reset! *set-texture (first (vals textures-map)))))
          on-load-complete (fn []
                             (swap! *callbacks conj :on-load-complete)
                             (</put! test-ch :load-complete))
          app-callbacks {:on-load-complete on-load-complete}]
      (lma/update-sys-config! sys loader-props {:a-texture bob-url} app-callbacks)
      (lma/-register-texture sys fake-sprite :a-texture)
      (async done
        (go (<! test-ch)
            (is (not= lma/empty-texture @*set-texture))
            (lma/update-sys-config! sys loader-props {:a-texture ninja-url} app-callbacks)
            (is (= lma/empty-texture @*set-texture))
            (destroy-sys sys)
            (done))))))

(deftest system-provides-sprite-sheet-frames
  (testing "Application loads and applies sprite sheet frames"
    (let [sys (lma/new-system)
          *set-textures (atom #{})
          *non-empty-textures (atom 0)
          open-while-loading (</chan)
          dummy-sprite (reify lma/IHasTextures
                         (-set-textures [_ textures-map]
                           (when (not= (first (vals textures-map)) lma/empty-texture)
                             (swap! *non-empty-textures inc))
                           (swap! *set-textures conj (first (keys textures-map)))))
          on-load-complete #(</close! open-while-loading)
          sys-callbacks {:on-load-complete on-load-complete}]
      (lma/update-sys-config! sys loader-props {:ninja ninja-sprite-sheet-url} sys-callbacks)
      (lma/-register-textures sys dummy-sprite [[:ninja "1.png"]
                                                [:ninja "2.png"]
                                                [:ninja "3.png"]
                                                [:ninja "4.png"]])
      (async done
        (go (<! open-while-loading)
            (is (= 4 @*non-empty-textures)
                "Four non-empty textures were set")
            (is (= #{[:ninja "1.png"]
                     [:ninja "2.png"]
                     [:ninja "3.png"]
                     [:ninja "4.png"]}
                   @*set-textures))
            (destroy-sys sys)
            (done))))))

;; XXX: Complete this test
(deftest system-makes-system-state-available
  (let [mount-point (dom/createElement "div")
        *state (atom {:initial-state 0})
        *states-read-from-gen (atom [])
        sys (lma/new-system *state)
        gen {:render (fn [props _]
                       (lma/assoc-in-state [:assoc-in] props)
                       (lma/update-in-state [:update-in] conj props)
                       (swap! *states-read-from-gen conj (lma/get-in-state []))
                       (lma/container {}))}
        render-ch (</chan)
        render-fn (fn [_]
                    (</put! render-ch :render)
                    [(lma/generator gen {:thing-1 1 :thing-2 2})])]

    (testing "generators can read and write state during render"
      (async done
        (lma/render sys mount-point render-fn)
        (go-loop [render-cnt 0]
          (when (< render-cnt 2)
            (let [_ (<! render-ch)]
              (condp = render-cnt
                0
                (do (is (= {:initial-state 0
                            :assoc-in {:thing-1 1, :thing-2 2}
                            :update-in [{:thing-1 1, :thing-2 2}]} @*state)
                        "State atom was updated during render")
                    (is (= @*states-read-from-gen
                           [{:initial-state 0}])
                        "The render function observed the initial state before render"))

                1
                (do (is (= {:initial-state 0
                            :assoc-in {:thing-1 1, :thing-2 2}
                            :update-in [{:thing-1 1, :thing-2 2}
                                        {:thing-1 1, :thing-2 2}]}
                           @*state)
                        "State atom was updated during second render")
                    (is (= @*states-read-from-gen
                           [{:initial-state 0}
                            {:initial-state 0
                             :assoc-in {:thing-1 1, :thing-2 2}
                             :update-in [{:thing-1 1, :thing-2 2}]}])
                        "The render function observed the previous state during render"))))

            (recur (inc render-cnt)))
          (destroy-sys sys)
          (</close! render-ch)
          (done))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Loader tests

(def ninja-spritesheet-url "/img/little_ninja.json")


(defn create-loader-callbacks
  [channel]
  (let [put-kv (fn [k v] (</put! channel [k v]))]
    {:on-start-loading #(put-kv :on-start-loading nil)
     :on-done-loading #(put-kv :on-done-loading nil)
     :on-loaded #(put-kv :on-loaded %)
     :on-unload #(put-kv :on-unload %)
     :on-progress #(put-kv :on-progress %)}))


(deftest resource-loader-loads-resources
  (testing "It can load a resource and all callbacks are fired"
    (let [loader (lma/new-loader loader-props)
          *progress (atom [])
          *callbacks (atom [])
          callback-ch (</chan)]
      (lma/set-callbacks! loader (create-loader-callbacks callback-ch))
      (lma/set-resources! loader {:bob bob-url, :ninja ninja-url})
      (async done
        (go-loop []
          (let [[cb arg] (<! callback-ch)]
            (when (not= cb :on-done-loading)
              (when (= :on-progress cb)
                (swap! *progress conj arg))
              (swap! *callbacks conj cb)
              (recur)))
          (is (= [50 100] @*progress) "Progress was reported in order")
          (is (= (set (keys (lma/loaded-resources loader))) #{:bob :ninja}))
          (is (= [:on-start-loading
                  :on-progress :on-loaded
                  :on-progress :on-loaded] @*callbacks)
              "All callbacks were called in the correct order")
          (let [ninja (lma/get-resource loader :ninja)
                bob (lma/get-resource loader :bob)]
            (is (instance? js/PIXI.Texture bob))
            (is (instance? js/PIXI.Texture ninja))
            (lma/set-resources! loader {})
            (is (= 0 (count (lma/loaded-resources loader)))
                "Resources have been unloaded")
            (is (nil? (.-baseTexture ninja))
                "Texture has been destroyed")
            (is (nil? (.-baseTexture  bob))
                "Texture has been destroyed"))
          (done))))))


(deftest resource-loader-can-load-more-resources
  (testing "The loader will continue loading resources if added during a load"
    (let [loader (lma/new-loader loader-props 1)
          callback-ch (</chan)]
      (lma/set-callbacks! loader (create-loader-callbacks callback-ch))
      (lma/set-resources! loader {:bob bob-url})
      (async done
        (go-loop [finish-count 0]
          (when (< finish-count 2)
            (let [[cb arg] (<! callback-ch)]
              (when (and (= 0 finish-count) (= :on-loaded cb))
                (lma/set-resources! loader {:bob bob-url, :ninja ninja-url}))
              (when (= cb :on-progress)
                (is (= 100 arg) "100% progress is reported for each load"))
              (if (= cb :on-done-loading)
                (recur (inc finish-count))
                (recur finish-count))))

          (is (= (set (keys (lma/loaded-resources loader))) #{:bob :ninja})
              "All resources are now contained in the loader")
          (is (instance? js/PIXI.Texture (lma/get-resource loader :bob)))
          (is (instance? js/PIXI.Texture (lma/get-resource loader :ninja)))
          (lma/set-resources! loader {})
          (done))))))


(deftest resource-loader-can-remove-resources
  (testing "A resource can be removed from the loader during a load cycle"
    (let [loader (lma/new-loader loader-props 1)
          callback-ch (</chan)
          *callbacks (atom [])]
      (lma/set-callbacks! loader (create-loader-callbacks callback-ch))
      (lma/set-resources! loader {:ninja ninja-url})
      (async done
        (go-loop [finish-count 0]
          (when (< finish-count 2)
            (let [[cb arg] (<! callback-ch)]
              (swap! *callbacks conj cb)
              (when (and (= 0 finish-count) (= :on-loaded cb))
                (lma/set-resources! loader {:bob bob-url}))
              (when (= cb :on-progress)
                (is (= 100 arg) "100% progress is reported for each load"))
              (when (= cb :on-unload)
                (is (= :ninja arg) "The correct resource is about to be unloaded"))
              (if (= cb :on-done-loading)
                (recur (inc finish-count))
                (recur finish-count))))
          (is (= (set (keys (lma/loaded-resources loader))) #{:bob})
              "Only the Bob remains")
          (is (instance? js/PIXI.Texture (lma/get-resource loader :bob)))
          (is (nil? (lma/get-resource loader :ninja)))
          (is (= [:on-start-loading :on-progress :on-loaded :on-done-loading
                  :on-unload
                  :on-start-loading :on-progress :on-loaded :on-done-loading]
                 @*callbacks)
              "All callbacks are called in the proper order")
          (lma/set-resources! loader {})
          (done))))))


(deftest resource-loader-error-callback
  (testing "The resource loader's on-error callback and influence a reload attempt"
    (let [loader (lma/new-loader loader-props)
          bad-resource {:dontexist "I don't exist"}
          *error-call-count (atom 0)
          callback-ch (</chan)
          on-error (fn [r _]
                     (swap! *error-call-count inc)
                     ;; Retry the loading on the first error
                     (when (= 1 @*error-call-count)
                       (lma/set-resources! loader bad-resource)))]
      (lma/set-callbacks! loader (merge {:on-error on-error}
                                        (create-loader-callbacks callback-ch)))
      (lma/set-resources! loader bad-resource)
      (async done
        (go-loop [finish-count 0]
          (if (< finish-count 2)
            (let [[cb arg] (<! callback-ch)]
              (if (= cb :on-done-loading)
                (recur (inc finish-count))
                (recur finish-count)))
            (do (is (= 2 @*error-call-count) "Error handler was called twice.")
                (done))))))))


(deftest resource-loader-handles-spritesheets
  (testing "The resource loader can load and store frames of a sprite sheet"
    (let [loader (lma/new-loader loader-props)
          callback-ch (</chan)
          *load-count (atom 0)]
      (lma/set-callbacks! loader (create-loader-callbacks callback-ch))
      (lma/set-resources! loader {:ninja ninja-spritesheet-url})
      (async done
        (go-loop []
          (let [[cb arg] (<! callback-ch)]
            (when (= cb :on-done-loading)
              (let [count (swap! *load-count inc)
                    sprite-sheet (lma/get-resource loader :ninja)
                    base-texture (lma/sprite-sheet-texture sprite-sheet)]
                (is (= (lma/resource-loaded? loader :ninja))
                    "Entire sprite sheet is loaded")
                (doseq [frame (lma/frames sprite-sheet)]
                  (is (= (lma/resource-loaded? loader [:ninja frame]))
                      (str "Loaded frame: " frame)))
                (lma/set-resources! loader {})
                (is (nil? (lma/get-resource loader :ninja))
                    "Sprite sheet is unloaded")
                ;; test to see if the base texture is unloaded from the resources
                (when (= 1 count)
                  (lma/set-resources! loader {:ninja ninja-spritesheet-url}))))
            (if (< @*load-count 2)
              ;; If the base texture isn't cleared from the loader then the
              ;; sprite sheet won't actually load again. Testing to ensure
              ;; this still works
              (recur)
              (do (lma/set-resources! loader {})
                  (done)))))))))

