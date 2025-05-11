A Scittle ClojureScript library for preloading and using SVG icons in your Reagent web apps.

## API

- `(load-icon "some.svg")` - macro that loads the SVG icon at compile time.
- `(wait-for-preload)` - macro that returns a promise at runtime which resolves once all icons are loaded.
- `[icon svg]` - hiccup component that creates a `[:i.icon ...svg...]` with the rendered SVG.
- `(set-svg-base-url url)` - macro to set base URL to load SVG icons from.

## Usage

### Basic Setup

```html
<script type="application/x-scittle" src="https://cdn.jsdelivr.net/gh/chr15m/scittle-iconloader/iconloader.cljs"></script>
```

```clojure
;; In your namespace declaration
(ns your-app
  (:require-macros
    [iconloader :refer [load-icon wait-for-preload]])
  (:require
    [iconloader :refer [icon]]
    [promesa.core :as p]))

;; Initialize your app after icons are loaded
(defn init! []
  (-> (wait-for-preload)
      (p/then (fn [_]
                ;; Icons are loaded, render your app
                (render-app!)))
      (p/catch (fn [error]
                 ;; Handle loading errors
                 (show-error error)))))
```

### Using Icons

```clojure
;; Load an icon (this expands to code that retrieves the SVG content)
(load-icon "path/to/icon.svg")

;; Use the icon in your Reagent component
[:div
 [icon (load-icon "path/to/icon.svg")]]

;; Or with dangerouslySetInnerHTML
[:div {:dangerouslySetInnerHTML 
       {:__html (load-icon "path/to/icon.svg")}}]
```

### Custom Base URL

```clojure
;; Set a base URL for all icons (call before any load-icon macros)
(iconloader/set-svg-base-url "/assets/icons/")

;; Load from tabler.io/icons:
(iconloader/set-svg-base-url ""https://cdn.jsdelivr.net/npm/@tabler/icons@3.31.0/icons/"")
```
