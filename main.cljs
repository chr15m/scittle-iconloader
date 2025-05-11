(ns main
  (:require
    [reagent.core :as r]
    [reagent.dom :as rdom]
    [promesa.core :as p]))

;; Atom to store SVG content, promises, or error markers
;; Key: icon-filename (string)
;; Value: SVG string | js/Promise | ::error
(defonce svg-data (atom {}))

;; Atom to store all unique fetch promises for wait-for-preload
(defonce svg-fetch-master-promise-list (atom []))

(defmacro svg-icon-source [icon-filename]
  (assert (string? icon-filename) "icon-filename must be a string.")

  ;; This code runs at macro expansion time (when Scittle parses this)
  (when-not (contains? @svg-data icon-filename)
    ;; If not already requested (or cached as final value/error)
    (js/console.log (str "[Macro svg-icon-source] Registering fetch for: " icon-filename))
    (let [fetch-promise (-> (js/fetch icon-filename)
                            (p/then (fn [response]
                                      (if (.-ok response)
                                        (.text response)
                                        (do
                                          (js/console.error (str "Failed to fetch SVG: " icon-filename ", status: " (.-status response)))
                                          (p/rejected (js/Error. (str "HTTP error " (.-status response) " for " icon-filename)))))))
                            (p/then (fn [svg-text]
                                      (js/console.log (str "[Macro svg-icon-source] Successfully fetched: " icon-filename))
                                      (swap! svg-data assoc icon-filename svg-text) ; Store final SVG text
                                      svg-text))
                            (p/catch (fn [error]
                                       (js/console.error (str "[Macro svg-icon-source] Error loading SVG: " icon-filename) error)
                                       (swap! svg-data assoc icon-filename ::error) ; Mark as error
                                       (p/rejected error))))] ; Propagate rejection for p/all

      ;; Store the promise in svg-data temporarily to indicate it's in flight.
      ;; This prevents re-triggering fetch if macro is called again for the same icon
      ;; during the initial parsing phase before this promise resolves.
      ;; It will be overwritten by the actual SVG text or ::error by the promise callbacks.
      (swap! svg-data assoc icon-filename fetch-promise)
      (swap! svg-fetch-master-promise-list conj fetch-promise)))

  ;; The macro expands to this code, which runs at runtime.
  ;; It retrieves the final resolved value (SVG string or ::error) from svg-data.
  `(let [data# (get @svg-data ~icon-filename)]
     (cond
       (instance? js/Promise data#)
       (do
         (js/console.warn (str "SVG " ~icon-filename " accessed before its promise resolved. Ensure wait-for-preload is awaited."))
         "<!-- SVG loading... -->") ; Placeholder for unresolved promise

       (= ::error data#)
       (do
         (js/console.error (str "SVG " ~icon-filename " failed to load, returning error placeholder."))
         "<!-- SVG error -->") ; Placeholder for error

       (string? data#)
       data# ; Actual SVG string

       :else
       (do
         (js/console.warn (str "SVG " ~icon-filename " not found in cache, returning empty string."))
         "<!-- SVG not found -->"))))

(defn wait-for-preload []
  (js/console.log (str "[wait-for-preload] Waiting for " (count @svg-fetch-master-promise-list) " SVG(s)."))
  (if (empty? @svg-fetch-master-promise-list)
    (p/resolved true) ; No SVGs to load
    (-> (p/all @svg-fetch-master-promise-list)
        (p/then (fn [results]
                  (js/console.log "[wait-for-preload] All SVG promises settled.")
                  results))
        (p/catch (fn [error]
                   ;; p/all rejects with the first error. Individual errors are already
                   ;; handled by svg-icon-source macro's promise chain (logged and ::error stored).
                   (js/console.error "[wait-for-preload] One or more SVGs failed to load overall." error)
                   (p/rejected error)))))) ; Propagate so caller of wait-for-preload can catch

;; --- Application Code ---
(defonce app-state (r/atom {:loaded? false :error nil}))

(defn app-component []
  [:div
   (if (:loaded? @app-state)
     [:main
      [:h1 "SVG Icon Demo"]
      [:p "Icon 'icon.svg':"]
      [:div {:dangerouslySetInnerHTML {:__html (svg-icon-source "icon.svg")}}]

      [:p "Icon 'nonexistent.svg' (should show error placeholder):"]
      [:div {:dangerouslySetInnerHTML {:__html (svg-icon-source "nonexistent.svg")}}]

      (when-let [err (:error @app-state)]
        [:div {:style {:color "red" :margin-top "1em" :font-weight "bold"}}
         "Error during preload phase: " (str err)])

      [:hr]
      [:h2 "Debug Info:"]
      [:h3 "Current content of " [:code "svg-data"] ":"]
      [:pre {:style {:border "1px solid #ccc", :padding "10px", :background-color "#f9f9f9"}}
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
