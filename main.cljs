(ns main
  (:require-macros
    [iconloader :refer [svg-icon wait-for-preload svg-data]])
  (:require
    [reagent.core :as r]
    [reagent.dom :as rdom]
    [promesa.core :as p]))

;; --- Application Code ---
(defonce app-state (r/atom {:loaded? false :error nil}))

(defn app-component []
  [:div
   (if (:loaded? @app-state)
     [:main
      [:h1 "SVG Icon Demo"]
      [:p "Icon 'icon.svg':"]
      [:div {:dangerouslySetInnerHTML {:__html (svg-icon "icon.svg")}}]

      [:p "Icon 'nonexistent.svg' (should show error placeholder):"]
      [:div {:dangerouslySetInnerHTML {:__html (svg-icon "nonexistent.svg")}}]

      (when-let [err (:error @app-state)]
        [:div {:style {:color "red" :margin-top "1em" :font-weight "bold"}}
         "Error during preload phase: " (str err)])

      [:hr]
      [:h2 "Debug Info:"]
      [:h3 "Current content of " [:code "svg-data"] ":"]
      [:code
       (pr-str @svg-data)]]
     [:div.loader "Loading SVGs and application..."])])

(defn init! []
  (js/console.log "App init sequence started.")
  ;; The macro expansions for svg-icon-source calls within app-component
  ;; have already occurred when Scittle parsed the definition of app-component.
  (-> (wait-for-preload)
      (p/then (fn [_results]
                (js/console.log "Preload function completed. Rendering app.")
                (swap! app-state assoc :loaded? true)))
      (p/catch (fn [error]
                 (js/console.error "Preload function failed overall. Rendering app with error message." error)
                 (swap! app-state assoc :loaded? true :error (.-message error))))
      (p/finally (fn []
                   (rdom/render [app-component] (.getElementById js/document "app"))))))

;; Start the app initialization.
(init!)
