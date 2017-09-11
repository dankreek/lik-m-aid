(ns lik-m-aid.core
  (:require [cljsjs.pixi]
            [rektify.core :as rekt]
            [clojure.data :as data]
            [clojure.set :as set]
            [clojure.string :as str]
            [goog.object :as object]
            [goog.dom :as dom]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants

(def set-prop!
  "Function used to set a property value on an object."
  object/set)


(def get-prop
  "Function used to get a property's value from an object."
  object/get)


(def empty-texture js/PIXI.Texture.EMPTY)


(def default-renderer
  (if ^:boolean (js/PIXI.utils.isWebGLSupported)
    js/PIXI.WebGLRenderer
    js/PIXI.CanvasRenderer))


(def blend-modes
  {:normal js/PIXI.BLEND_MODES.NORMAL
   :add js/PIXI.BLEND_MODES.ADD
   :multiply js/PIXI.BLEND_MODES.MULTIPLY
   :screen js/PIXI.BLEND_MODES.SCREEN
   :overlay js/PIXI.BLEND_MODES.OVERLAY
   :darken js/PIXI.BLEND_MODES.DARKEN
   :lighten js/PIXI.BLEND_MODES.LIGHTEN
   :color-dodge js/PIXI.BLEND_MODES.COLOR_DODGE
   :color-burn js/PIXI.BLEND_MODES.COLOR_BURN
   :hard-light js/PIXI.BLEND_MODES.HARD_LIGHT
   :soft-light js/PIXI.BLEND_MODES.SOFT_LIGHT
   :difference js/PIXI.BLEND_MODES.DIFFERENCE
   :exclusion js/PIXI.BLEND_MODES.EXCLUSION
   :hue js/PIXI.BLEND_MODES.HUE
   :saturation js/PIXI.BLEND_MODES.SATURATION
   :color js/PIXI.BLEND_MODES.COLOR
   :luminosity js/PIXI.BLEND_MODES.LUMINOSITY})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resource loader types

(def ^:dynamic *current-sys* nil)


(defprotocol IResourceRegistry
  (-unregister-object
    [this pixi-object]
    "Unregisters the given object from all loaded resources."))


(defn unregister-object
  [pixi-object]
  (when *current-sys*
    (-unregister-object *current-sys* pixi-object)))


(defprotocol ITextureRegistry
  (-register-texture
    [this pixi-object asset-key]
    "Registers the given asset key for the provided pixi object. If any other
    keys are registered for the object they will all be dissociated from object
    in the registry.

    Returns a map with a single key, which is the `asset-key` and a texture as
    its value. If the texture will either be empty, if it's not yet loaded or
    a reference to the loaded texture.")

  (-register-textures
    [this pixi-object asset-keys]
    "Register a sequence of texture keys for the provided object. Any keys
    previously registered to the object will be removed from the registry.

    A map of assets-keys -> textures is returned. For every key that has been
    loaded the requested texture will be available, otherwise and empty
    texture."))

(defn register-texture
  [pixi-object asset-key]
  (when *current-sys*
    (-register-texture *current-sys* pixi-object asset-key)))

(defn register-textures
  [pixi-object asset-keys]
  (when *current-sys*
    (-register-textures *current-sys* pixi-object asset-keys)))

(defprotocol IHasTextures
  (-set-textures
    [this textures-map]
    "Gives the object a map of asset keys -> textures that have been requested."))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; General PixiJS display object functions

(defn pixi-add-child!
  "Add a child to a pixi object"
  [pixi-obj pixi-child]
  (.addChild pixi-obj pixi-child))


(defn pixi-child-index
  "Return the index of the child object in the parent's child list."
  [pixi-obj child]
  (.getChildIndex pixi-obj child))


(defn pixi-remove-child-at!
  [pixi-obj child-index]
  (.removeChildAt pixi-obj child-index))


(defn pixi-get-parent
  [pixi-obj]
  (.-parent pixi-obj))


(defn pixi-get-children
  [pixi-obj]
  (.-children pixi-obj))


(defn pixi-replace-child-at!
  "Given a parent Pixi object replace the child at the provided index with the
  new child."
  [parent-pixi-obj new-child index]
  (when (not= (.getChildAt parent-pixi-obj index) new-child)
    (.removeChildAt parent-pixi-obj index)
    (.addChildAt parent-pixi-obj new-child index)))


(defn pixi-destroy!
  [pixi-obj]
  (.destroy pixi-obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setter and getter functions

(defn- set-blend-mode!
  [pixi-obj property val]
  (let [pixi-val (get blend-modes val)]
    (if (nil? pixi-val)
      (throw (js/Error. (str "Blend mode can only be one of the following values: "
                             (str/join ", " (keys blend-modes)))))
      (set-prop! pixi-obj property (get blend-modes val)))))


(defn- get-blend-mode
  [pixi-obj property]
  (let [pixi-blend-mode (get-prop pixi-obj property)]
    (first (filter #(= (get blend-modes %) pixi-blend-mode) (keys blend-modes)))))


(defn- set-point!
  "The property setter for the PixiJS Point/ObserveablePoint types.
  The value of this setter can be either a vector of two numbers in the form of
  [X Y], a single number which will be set to both coordinates, or an instance
  of a PixiJS Point or ObserveablePoint, in which case the X and Y coordinates
  will be copied to the destination object."
  [pixi-obj path val]
  (let [dest-point (get-prop pixi-obj path)]
    (cond
      (number? val)
      (.set dest-point val)

      (and (vector? val) (= 2 (count val)))
      (.set dest-point (first val) (nth val 1))

      (or (instance? js/PIXI.ObservablePoint val)
          (instance? js/PIXI.Point val))
      (.copy dest-point val)

      :else
      (throw
        (js/Error. (str "A Point/ObserveablePoint type must be a single number, "
                        "a vector of size 2 or a Point/ObserveablePoint object. "
                        "Tried to apply: " val " to " path))))))


(defn- get-point
  "This function has the same signature as `aget` but expects to find a PixiJS
  Point object. Returns a size-2 vector consisting of the coodinated in the
  form of [X Y]. Throws an exception if a Point type is not found."
  [pixi-obj property]
  (let [point (get-prop pixi-obj property)]
    (if (or (instance? js/PIXI.ObservablePoint point)
            (instance? js/PIXI.Point point))
      [(.-x point) (.-y point)]
      (throw (js/Error. (str "A Point/ObserveablePoint type was expected for property: " property))))))


(defn- set-texture-resource
  "Uses the system's loader to set a texture on an object."
  [pixi-obj _ resource-key]
  (assert (or (keyword? resource-key)
              (and (vector? resource-key) (keyword? (first resource-key))))
          "Either a keyword or a [:keyword \"frame name\"] vector must be specified for the :texture property.")
  (let [texture (register-texture pixi-obj resource-key)]
    (set! (.-texture pixi-obj) (get texture resource-key))))


(defn- set-textures-list
  "Use the system's loader to set an animated sprites list of textures."
  [pixi-obj _ resource-keys]
  (assert (vector? resource-keys)
          "An animate sprite's texture list must be a vector of texture keys.")
  (let [textures (register-textures pixi-obj resource-keys)]
    ;; XXX: should the array be reset or manipulated?
    (set! (.-textures pixi-obj)
          (clj->js (mapv (fn [key] (get textures key)) resource-keys)))
    (.play pixi-obj)))


(defn- set-animation-speed
  [pixi-obj _ speed]
  (set! (.-animationSpeed pixi-obj) speed))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Property configs

(def display-object-prop-map
  {:alpha {:property "alpha" :setter set-prop! :getter get-prop}
   :button-mode? {:property "buttonMode" :setter set-prop! :getter get-prop}
   :cache-as-bitmap? {:property "cacheAsBitmap" :setter set-prop! :getter get-prop}
   :cursor {:property "cursor" :setter set-prop! :getter get-prop}
   :mask {:property "mask" :setter set-prop! :getter get-prop}
   :pivot {:property "pivot" :setter set-point! :getter get-point}
   :renderable? {:property "renderable" :setter set-prop! :getter get-prop}
   :rotation {:property "rotation" :setter set-prop! :getter get-prop}
   :scale {:property "scale" :setter set-point! :getter get-point}
   :skew {:property "skew" :setter set-point! :getter get-point}
   :visible? {:property "visible" :setter set-prop! :getter get-prop}
   :x {:property "x" :setter set-prop! :getter get-prop}
   :y {:property "y" :setter set-prop! :getter get-prop}})


(def container-prop-map
  (merge display-object-prop-map
         {:width {:property "width" :setter set-prop! :getter get-prop}
          :height {:property "height" :setter set-prop! :getter get-prop}}))


(def sprite-prop-map
  (merge container-prop-map
         {:anchor {:property "anchor" :setter set-point! :getter get-point}
          :tint {:property "tint" :setter set-prop! :getter get-prop}
          :blend-mode {:property "blendMode" :setter set-blend-mode! :getter get-blend-mode}
          :texture {:property "texture" :setter set-texture-resource}
          :texture-obj {:property "texture" :getter get-prop}}))


(def tiling-sprite-prop-map
  (merge sprite-prop-map
         {:tile-scale {:property "tileScale" :setter set-point! :getter get-point}
          :tile-position {:property "tilePosition" :setter set-point! :getter get-point}}))


(def animated-sprite-prop-map
  (-> sprite-prop-map
      (dissoc :texture :texture-obj)
      (merge {:on-loop {:property "onLoop" :setter set-prop! :getter get-prop}
              :animation-speed {:property "animationSpeed" :setter set-animation-speed :getter get-prop}
              :textures {:property "textures" :setter set-textures-list}
              :textures-arr {:property "textures" :getter get-prop}})))


(def container-object-child-accessors
  {:add-child pixi-add-child!
   :child-index pixi-child-index
   :replace-child-at pixi-replace-child-at!
   :remove-child-at pixi-remove-child-at!
   :get-parent pixi-get-parent
   :get-children pixi-get-children})


(def container-config
  (merge
    container-object-child-accessors
    {:prop-map container-prop-map
     :constructor js/PIXI.Container
     :destructor pixi-destroy!
     :default-props (rekt/get-existing-object-properties
                      (new js/PIXI.Container)
                      container-prop-map)}))


(def single-texture-fns
  (merge
    container-object-child-accessors
    {:post-constructor (fn [this]
                         (specify! this
                           IHasTextures
                           (-set-textures [this textures-map]
                             (set! (.-texture this) (first (vals textures-map))))))
     :destructor (fn [this]
                   (unregister-object this)
                   (pixi-destroy! this))}))

(def sprite-config
  (merge
    single-texture-fns
    {:prop-map sprite-prop-map
     :constructor js/PIXI.Sprite
     :constructor-list [[:texture-obj]]
     :default-props (rekt/get-existing-object-properties
                      (js/PIXI.Sprite. empty-texture)
                      sprite-prop-map)}))

(def tiling-sprite-config
  (merge
    single-texture-fns
    {:prop-map tiling-sprite-prop-map
     :constructor js/PIXI.extras.TilingSprite
     :constructor-list [[:texture-obj]
                        [:texture-obj :width :height]]
     :default-props (assoc
                      (rekt/get-existing-object-properties
                         (js/PIXI.extras.TilingSprite. empty-texture)
                         tiling-sprite-prop-map)
                      :texture nil)}))


(def animated-sprite-config
  (merge
    container-object-child-accessors
    {:prop-map animated-sprite-prop-map
     :constructor js/PIXI.extras.AnimatedSprite
     :constructor-list [[:textures-arr]]
     :post-constructor (fn [this]
                         (specify! this
                           IHasTextures
                           (-set-textures [this textures-map]
                             (let [textures-list (rekt/get-object-prop this :textures)
                                   textures-arr (.-textures this)]
                               (loop [i 0]
                                 (when-let [key (nth textures-list i false)]
                                   (when (contains? textures-map key)
                                     (aset textures-arr i (get textures-map key))
                                     ;; XXX: set-prop! also works with arrays?
                                     #_(set-prop! textures-arr i (get textures-map key))

                                     )
                                   (recur (inc i))))))))
     :destructor (fn [this]
                   (unregister-object this)
                   (pixi-destroy! this))
     :default-props (assoc
                      (rekt/get-existing-object-properties
                        (js/PIXI.extras.AnimatedSprite. #js [empty-texture])
                        animated-sprite-prop-map)
                      :textures [])}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Loader

(defprotocol IPixiLoader
  (set-callbacks!
    [this callback-map]
    "Set all the callback functions for this object.")

  (resource-in-loader?
    [this resource-key]
    "Returns `true` if the request key is either loaded, or queued up to be
    loaded, otherwise returns `false`.")

  (resource-loaded?
    [this resource-key]
    "Returns `true` if the given `resource-key` has been loaded, `false` if it
    has yet to be loaded, or isn't set to load.")

  (loaded-resources
    [this]
    "Return the map of loaded resources.")

  (get-resource
    [this resource-key]
    "Return a loaded resource identified by its key. If the resource has not yet
    been loaded return `nil`.")

  (set-resources!
    [this resources]
    "A map of resources that should be loaded. When this is updated, all
    resources that are no longer needed will be destroyed and all resources that
    have been newly added will be queued up to load."))


(defprotocol IPixiResource
  (resource-destroy
    [this loader resource-name]
    "Destroy this resource located in the provided loader with the name of
    `resource-name`.")

  (resource-child-keys
    [this resource-key]
    "Return a list of keys which are children of this resource."))


(defprotocol ISpriteSheet
  (frames
    [this]
    "Get a list of all the frames in this spritesheet.")

  (sprite-sheet-texture
    [this]
    "Get the texture for the entire sprite sheet"))


(defn extend-sprite-sheet!
  [sprite-sheet]
  (specify! sprite-sheet
    ISpriteSheet
    (frames [this]
      (js->clj (.-_frameKeys this)))

    (sprite-sheet-texture [this]
      (.-baseTexture this))

    IPixiResource
    (resource-child-keys
      [this resource-key]
      (mapv (fn [frame] [resource-key frame]) (.-_frameKeys this)))

    (resource-destroy
      [this loader resource-name]
      (let [resources (.-resources loader)
            base-image-name (str resource-name "_image")]
        (object/remove resources resource-name)
        (.destroy (.-texture (object/get resources base-image-name)))
        (object/remove resources base-image-name))
      (.destroy this true))))


(defn- extend-texture!
  [texture]
  (specify! texture
    IPixiResource
    (resource-child-keys [_ _] [])
    (resource-destroy
      [this loader resource-name]
      (let [resources (.-resources loader)]
        (.destroy (.-texture (object/get resources resource-name)) true)
        (object/remove resources resource-name)
        (.destroy this)))))


(defn- resource-key->name
  [loader-key]
  (str loader-key))


(defn- resource-name->key
  [resource-key]
  (keyword (subs resource-key 1)))


(defn- get-resource-key
  [resource]
  (resource-name->key (.-name resource)))


(defn- get-resource-type
  [resource]
  (condp = (.-type resource)
    js/PIXI.loaders.Resource.TYPE.JSON
    :json

    js/PIXI.loaders.Resource.TYPE.IMAGE
    :image

    :unknown))


(defn- get-resource-object
  [resource]
  (let [type (get-resource-type resource)]
    (condp = type
      :image
      (.-texture resource)

      :json
      (.-spritesheet resource)

      nil)))


(defn- get-object-from-loader
  [loader-obj resource-name]
  (when-let [resource (object/get (.-resources loader-obj) resource-name)]
    (get-resource-object resource)))


(defn- start-load-cycle!
  "If the PixiJS Loader is not currently loading, remove all the resources
  that need to be removed, add all the resources that need to be added and
  start loading."
  [loader-obj *loader-state *callbacks]
  (when (not (.-loading loader-obj))
    (let [on-unload (:on-unload @*callbacks)]
      (swap! *loader-state
             (fn [{:keys [to-load to-remove]}]
               ;; Destroy and remove resources from the PixiJS Loader after callback
               (doseq [resource-key to-remove]
                 (let [resource-name (resource-key->name resource-key)
                       resource (get-object-from-loader loader-obj resource-name)]
                   (when on-unload
                     (when resource
                       (doseq [child-key (resource-child-keys resource resource-key)]
                         (on-unload child-key)))
                     (on-unload resource-key))
                   (when resource
                     (resource-destroy resource loader-obj resource-name))))
               (when (not (empty? to-load))
                 ;; Add resources to load
                 (doseq [[resource-key url] to-load]
                   (.add loader-obj (resource-key->name resource-key) url))
                 ;; For some reason the loader doesn't reset its progress by itself
                 (set! (.-progress loader-obj) 0)
                 ; Start loading
                 (.load loader-obj))
               ;; Return an empty state
               {})))
    nil))


(defn loader-resources-validator
  [resources]
  (assert (map? resources)
          "Loader resources need to be defined as a map.")
  ;; TODO: Ensure the keys and values are valid, throw error with bad data included in the message
  true)


(defn new-loader-resources
  "Create a new atom for the loader which contains a watch that maintains the
  list of resources to remove and add from the PixiJS Loader as well as removes
  resources from *loaded-resources when they are no longer current."
  [loader-obj *loader-state *loaded-resources *callbacks]
  (let [*resources (atom {} :validator loader-resources-validator)
        watch (fn [_ _ prev-desired desired]
                (let [[changed-and-removed changed-and-new] (data/diff prev-desired desired)
                      changed-and-removed-keys (set (keys changed-and-removed))]
                  ;; Remove changed things from the loaded resources
                  (swap! *loaded-resources
                         #(apply dissoc % changed-and-removed-keys))
                  ;; Add changed and removed things to the :to-remove list
                  (swap! *loader-state
                         update-in [:to-remove] set/union changed-and-removed-keys)
                  ;; Add updated and added things to the :to-load map
                  (swap! *loader-state
                         update-in [:to-load] merge changed-and-new))
                (start-load-cycle! loader-obj *loader-state *callbacks))]
    (add-watch *resources :resources watch)))


(def valid-loader-callbacks #{:on-start-loading :on-done-loading
                              :on-loaded :on-unload
                              :on-progress
                              :on-error})


(defn loader-callback-validator
  [callbacks]
  (when (not (map? callbacks))
    (throw (js/Error. "Loader callbacks must be a map.")))
  (let [callback-keys (set (keys callbacks))
        invalid-callbacks (set/difference callback-keys valid-loader-callbacks)]
    (if (not= #{} invalid-callbacks)
      (throw (js/Error. (str "Invalid callbacks specified on loader: "
                             (str/join ", " invalid-callbacks))))
      true)))


(def loader-prop-map
  {:base-url {:property "baseUrl" :setter set-prop! :getter get-prop}})


(def loader-config
  {:prop-map loader-prop-map
   :default-props (rekt/get-existing-object-properties js/PIXI.loader loader-prop-map)})


(defn get-resource-map-from-resource
  "Given a resource, return a map of all the resource objects contained in the
  resource keyed with their resource-keys."
  [resource]
  (let [main-key (get-resource-key resource)]
    (condp = (get-resource-type resource)
      :image
      {main-key (extend-texture! (.-texture resource))}

      :json
      (let [sprite-sheet (.-spritesheet resource)]
        (into {main-key (extend-sprite-sheet! sprite-sheet)}
              (mapv (fn [[k v]]
                      [[main-key k] v])
                    (js->clj (.-textures sprite-sheet)))))

      (throw (js/Error. (str "Could not parse resource type with key: " main-key))))))


(defn extend-loader!
  "Extend an instance of `PIXI.loaders.Loader` with the `IPixiObject` and
  `ILoader` protocols implemented."
  [loader-obj]
  (let [extended-loader (rekt/extend-existing-obj! loader-obj loader-config)
        *loader-state (atom {})
        *callbacks (atom {} :validator loader-callback-validator)
        *loaded-resources (atom {})
        *resources (new-loader-resources loader-obj *loader-state *loaded-resources *callbacks)
        on-start (fn [_] (when-let [cb (:on-start-loading @*callbacks)] (cb)))
        on-complete (fn [_ _]
                      (when-let [cb (:on-done-loading @*callbacks)] (cb))
                      ;; If new resources need to be loaded or destroyed, attempt another load cycle
                      (start-load-cycle! loader-obj *loader-state *callbacks))
        on-load (fn [_ resource]
                  (when (not (str/ends-with? (.-name resource) "_image"))
                    (let [new-resources (get-resource-map-from-resource resource)]
                      (swap! *loaded-resources merge new-resources)
                      (when-let [cb (:on-loaded @*callbacks)]
                        (doseq [resource-key (keys new-resources)]
                          (cb resource-key))))))
        on-error (fn [error _ failed-resource]
                   (let [resource-key (get-resource-key failed-resource)]
                     (swap! *resources dissoc resource-key)
                     (object/remove (.-resources loader-obj) (.-name failed-resource))
                     (when-let [cb (:on-error @*callbacks)]
                       (cb resource-key error))))
        on-progress (fn [_ _]
                      (when-let [cb (:on-progress @*callbacks)]
                        (cb (.-progress loader-obj))))]
    (.add (.-onStart loader-obj) on-start)
    (.add (.-onComplete loader-obj) on-complete)
    (.add (.-onLoad loader-obj) on-load)
    (.add (.-onProgress loader-obj) on-progress)
    (.add (.-onError loader-obj) on-error)
    (specify! extended-loader
      IPixiLoader
      (set-callbacks!
        [this callback-map]
        (reset! *callbacks callback-map)
        nil)

      (resource-in-loader?
        [this resource-key]
        (contains? @*resources resource-key))

      (resource-loaded?
        [this resource-key]
        (contains? @*loaded-resources resource-key))

      (loaded-resources [this] @*loaded-resources)

      (get-resource
        [this resource-key]
        (get @*loaded-resources resource-key))

      (set-resources!
        [this resources]
        (reset! *resources resources)))))


(defn new-loader
  "Create a new Loader with the IPixiObject and ILoader protocols
  implemented. Can optionally accept a props map, currently only `:base-url` is
  used and a concurrency limit, which the maximum number of HTTP requests sent
  out at a time. Concurrency can't be changed after instantiation."
  ([] (new-loader {}))
  ([props]
   (new-loader props 10))
  ([props concurrency]
   (let [loader (new js/PIXI.loaders.Loader "" concurrency)
         extended-loader (extend-loader! loader)]
     (rekt/apply-props! extended-loader props)
     loader)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Renderer Rektify config

(defn- get-renderer-dimensions
  "Gets a PixiJS Renderer's dimensions. This an interesting case since the width
  and height are gotten via read-only properties, but can only be set as a pair.
  This getter returns a size-2 vector in the form of `[width height]`."
  [pixi-renderer _]
  (assert (instance? default-renderer pixi-renderer))
  [(.-width pixi-renderer) (.-height pixi-renderer)])


(defn- set-renderer-dimensions
  "Since the PixiJS Renderer's dimensions can only be set as a pair this setter
  takes a size-2 vector in the form of `[width height]`."
  [pixi-renderer _ [width height]]
  (.resize pixi-renderer width height))


(def renderer-prop-map
  {:dimensions {:setter set-renderer-dimensions :getter get-renderer-dimensions}
   :transparent? {:property "transparent" :setter set-prop! :getter get-prop}
   :auto-resize? {:property "autoResize" :setter set-prop! :getter get-prop}
   :pixel-ratio {:property "resolution" :setter set-prop! :getter get-prop}
   :clear-before-render? {:property "clearBeforeRender" :setter set-prop! :getter get-prop}
   :background-color {:property "backgroundColor" :setter set-prop! :getter get-prop}
   :round-pixels? {:property "roundPixels" :setter set-prop! :getter get-prop}})


(def renderer-config
  {:prop-map renderer-prop-map
   :default-props (merge
                    (rekt/get-existing-object-properties
                      (new default-renderer)
                      renderer-prop-map))})


(defn extend-renderer!
  [pixi-renderer]
  (rekt/extend-existing-obj! pixi-renderer renderer-config))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; System

(defprotocol ISystem
  (-render-cb
    [this]
    "Return this system's render callback")

  (render [this parent-elem render-cb]
    "Start the rendering loop using the provided `render-cb` as the render
    callback and `parent-elem` as the DOM element which should have the canvas
    mounted under it.")

  (dimensions [this]
    "Returns the dimensions of the render canvas as a size-2 vector in the form
    of `[width height]`.")

  (start [this]
    "Start the rendering loop if it has been stopped.")

  (stop [this]
    "Stop the rendering loop.")

  (update-sys-config!
    ;; TODO: Docs
    [this props resources]
    [this props resources callbacks]))


(defn- map-vals
  [m f]
  (zipmap (keys m) (map f (vals m))))


(defn- remove-object-from-asset-registry
  [registry object]
  (map-vals registry (fn [objs] (filter #(not= % object) objs))))


(defn- add-object-to-asset-registry
  [registry object asset-keys]
  (loop [keys asset-keys
         reg registry]
    (if-let [key (first keys)]
      (recur (rest keys) (update-in reg [key] #(conj % object)))
      reg)))


(deftype System [app *props *callbacks *render-cb stage *registered-textures]
  ISystem
  (-render-cb [this]
    (fn [time-delta]
      (when-let [render-cb @*render-cb]
        (binding [*current-sys* this]
          (let [v-graph (render-cb time-delta)]
            (assert (and (rekt/virtual-graph? v-graph)
                         (= container-config (rekt/virtual-node-type-desc v-graph)))
                    "The render callback must return a Container as the root of the virtual graph")
            (rekt/re-render-graph! stage v-graph))))))

  (render [this mount-elem render-cb]
    (assert (instance? js/HTMLElement mount-elem)
            "The provided mount point to is not a valid DOM element.")
    (assert (fn? render-cb)
            "The provided rendering callback is not a function.")
    (reset! *render-cb render-cb)
    (dom/removeChildren mount-elem)
    (dom/insertChildAt mount-elem (.-view app) 0))

  (update-sys-config!
    [this props resources]
    (update-sys-config! this props resources {}))

  (update-sys-config!
    [this props resources callbacks]
     (let [stage-props (select-keys props (keys container-prop-map))
           renderer-props (select-keys props (keys renderer-prop-map))
           loader-props (select-keys props (keys loader-prop-map))]
       (reset! *callbacks callbacks)
       (reset! *props props)
       (rekt/apply-props! (.-renderer app) renderer-props)
       (rekt/apply-props! (.-stage app) stage-props)
       (rekt/apply-props! (.-loader app) loader-props)
       (set-resources! (.-loader app) resources)))

  (dimensions [this] (rekt/get-object-prop (.-renderer app) :dimensions))
  (start [this] (.start app))
  (stop [this] (.stop app))

  IResourceRegistry
  (-unregister-object
    [this pixi-object]
    (swap! *registered-textures remove-object-from-asset-registry pixi-object))

  ITextureRegistry
  (-register-textures
    [this pixi-object resource-keys]
    (assert (satisfies? IHasTextures pixi-object)
            "The pixi object does not have textures.")
    (swap!
      *registered-textures
      (fn [current-registry]
        (doseq [key resource-keys]
          (let [loader (.-loader app)
                main-key (if (keyword? key) key (first key))]
            (when (not (resource-in-loader? loader main-key))
              (js/console.warn (str "The requested resource key " main-key
                                    " does not exist in the resource loader.")))
            (let [texture (if (resource-loaded? loader main-key)
                            (get-resource loader key)
                            empty-texture)]
              (when (nil? texture)
                (js/console.warn (str "The requested frame '" (second key)
                                      "' does not exist in the sprite sheet " (first key) ))))))
        (-> current-registry
            (remove-object-from-asset-registry pixi-object)
            (add-object-to-asset-registry pixi-object resource-keys))))
    (into {} (mapv
               (fn [k]
                 [k (or (get-resource (.-loader app) k)
                        empty-texture)])
               resource-keys)))

  (-register-texture
    [this pixi-object resource-key]
    (-register-textures this pixi-object [resource-key])))


(def system-callbacks
  "The set of callback keywords that can be used with the system."
  ;; TODO: Fully document callback arguments
  ;; TODO: Figure out what happens when a callback fn w/the wrong arity is used and try and throw a good error message
  #{:on-load-error
    :on-load-resource
    :on-load-progress
    :on-load-start
    :on-load-complete})


(defn system-callbacks-validator
  [new-callback-map]
  (assert
    (every? system-callbacks (keys new-callback-map))
    (str "One or more invalid system callbacks were specified: "
         (str/join ", " (set/difference (keys new-callback-map) system-callbacks))
         ". Valid callback keys are "
         (str/join ", " system-callbacks) "."))
  true)


(def system-props
  (set (set/union (keys renderer-prop-map)
                  (keys loader-prop-map)
                  (keys container-prop-map))))


(defn system-props-validator
  [new-props]
  (assert
    (set/subset? (keys new-props) system-props)
    (str "Invalid system properties were specified: "
         (str/join ", " (set/difference (set (keys new-props)) system-props))
         ". Valid properties are: "
         (str/join ", " system-props) "."))
  true)


(defn render-cb-validator
  [new-cb]
  (assert
    (or (nil? new-cb) (fn? new-cb))
    "The system's render callback must be either set to `nil` or a function which accepts `time-delta` as a parameter.")
  true)


(defn create-system
  ([] (create-system false))
  ([anti-alias?]
    ;; TODO: stupid work-around... fix in rektify later
   (swap! rekt/*generator-registry {})
   (let [pixi-app (new js/PIXI.Application #js {"antialias" anti-alias?})
         stage (rekt/extend-existing-graph-obj!
                 (.-stage pixi-app) container-config)
         loader (extend-loader! (.-loader pixi-app))
         renderer (extend-renderer! (.-renderer pixi-app))
         *callbacks (atom {} :validator system-callbacks-validator)
         *props (atom {} :validator system-props-validator)
         *render-cb (atom nil :validator render-cb-validator)
         *registered-textures (atom {})
         new-system (->System pixi-app *props *callbacks *render-cb stage *registered-textures)
         on-error (fn [key e]
                    (when-let [cb (:on-load-error @*callbacks)] (cb key e)))
         on-loaded (fn [key]
                     (let [resource-obj (get-resource loader key)]
                       (doseq [pixi-obj (get @*registered-textures key)]
                         (-set-textures pixi-obj {key resource-obj})))

                     (when-let [cb (:on-load-resource @*callbacks)] (cb key)))
         on-unload (fn [key]
                     (let [pixi-objects (get @*registered-textures key)]
                       (when (and (seq pixi-objects) (not (resource-in-loader? loader key)))
                         (js/console.warn
                           "The texture with the key" (str key) "has been unloaded while there are still objects registered as needing it"))
                       (doseq [pixi-obj pixi-objects]
                         (-set-textures pixi-obj {key empty-texture}))))
         on-progress (fn [percent]
                       (when-let [cb (:on-load-progress @*callbacks)] (cb percent)))
         on-done-loading (fn [] (when-let [cb (:on-load-complete @*callbacks)] (cb)))
         on-start-loading (fn [] (when-let [cb (:on-load-start @*callbacks)] (cb)))]
     (set-callbacks! loader {:on-error on-error
                             :on-loaded on-loaded
                             :on-unload on-unload
                             :on-progress on-progress
                             :on-start-loading on-start-loading
                             :on-done-loading on-done-loading})
     ;; TODO: Put actual loader callbacks in system and refer to them like -render-cb below
     (.add (.-ticker pixi-app) (-render-cb new-system) nil (inc js/PIXI.UPDATE_PRIORITY.LOW))
     new-system)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Virtual graph nodes

(defn container
  [props & children]
  (rekt/object-v-node container-config props (vec children)))


(defn sprite
  [props & children]
  (rekt/object-v-node sprite-config props children))


(defn tiling-sprite
  [props & children]
  (rekt/object-v-node tiling-sprite-config props children))


(defn animated-sprite
  [props & children]
  (rekt/object-v-node animated-sprite-config props children))


(defn generator
  ([generator-def]
   (generator generator-def {}))
  ([generator-def props]
   (rekt/generator-v-node generator-def props)))

