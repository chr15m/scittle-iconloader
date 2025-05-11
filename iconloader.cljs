(ns iconloader
  (:require
    [promesa.core :as p]))

;; Atom to store the base URL for SVG icons.
;; Default is "", meaning relative to the current path.
(defonce svg-base-url (atom ""))

;; Function to set the base URL for SVG icons.
;; Call this before load-icon macros are expanded if a custom base URL is needed.
;; Example: (set-svg-base-url "/assets/icons/")
(defn set-svg-base-url [new-url]
  (assert (string? new-url) "Base URL must be a string.")
  (reset! svg-base-url new-url)
  nil)

;; Atom to store SVG content, promises, or error markers
;; Key: full-icon-path (string, e.g., "/base/path/icon.svg")
;; Value: SVG string | js/Promise | ::error
(defonce svg-data (atom {}))

;; Atom to store all unique fetch promises for wait-for-preload
(defonce svg-fetch-master-promise-list (atom []))

(defmacro load-icon [icon-filename]
  (assert (string? icon-filename) "icon-filename must be a string.")

  ;; Read the base URL at macro expansion time
  (let [current-base-url @svg-base-url
        full-icon-path (str current-base-url icon-filename)]

    ;; This code runs at macro expansion time (when Scittle parses this)
    (when-not (contains? @svg-data full-icon-path)
      ;; If not already requested (or cached as final value/error for this path)
      (js/console.log (str "[Macro load-icon] Registering fetch for: "
                           full-icon-path))
      (let [fetch-promise
            (-> (js/fetch full-icon-path) ; Use full-icon-path
                (p/then (fn [response]
                          (if (.-ok response)
                            (.text response)
                            (do
                              (js/console.error (str "Failed to fetch SVG: "
                                                     full-icon-path
                                                     ", status: "
                                                     (.-status response)))
                              (p/rejected
                                (js/Error. (str "HTTP error "
                                                (.-status response)
                                                " for "
                                                full-icon-path)))))))
                (p/then (fn [svg-text]
                          (js/console.log
                            (str "[Macro load-icon] Successfully fetched: "
                                 full-icon-path))
                          ; Store final SVG text using full-icon-path
                          (swap! svg-data assoc full-icon-path svg-text)
                          svg-text))
                (p/catch (fn [error]
                           (js/console.error
                             (str "[Macro load-icon] Error loading SVG: "
                                  full-icon-path) error)
                           ; Mark as error using full-icon-path
                           (swap! svg-data assoc full-icon-path ::error)
                           (p/rejected error))))] ; Propagate rejection for p/all

        ;; Store the promise in svg-data temporarily using full-icon-path.
        (swap! svg-data assoc full-icon-path fetch-promise)
        (swap! svg-fetch-master-promise-list conj fetch-promise)))

    ;; The macro expands to this code, which runs at runtime.
    ;; It retrieves the final resolved value (SVG string or ::error) from svg-data
    ;; using the full-icon-path that was determined at macro expansion time.
    `(let [data# (get @svg-data ~full-icon-path)]
       (cond
         (instance? js/Promise data#)
         (do
           (js/console.warn (str "SVG " ~full-icon-path
                                 " accessed before its promise resolved."
                                 "Ensure wait-for-preload is awaited."))
           "<!-- SVG loading... -->") ; Placeholder for unresolved promise

         (= ::error data#)
         (do
           (js/console.error
             (str "SVG " ~full-icon-path
                  " failed to load, returning error placeholder."))
           "<!-- SVG error -->") ; Placeholder for error

         (string? data#)
         data# ; Actual SVG string

         :else
         (do
           (js/console.warn (str "SVG "
                                 ~full-icon-path
                                 " not found in cache, returning empty string."))
           "<!-- SVG not found -->")))))

(defn wait-for-preload []
  (js/console.log
    (str "[wait-for-preload] Waiting for "
         (count @svg-fetch-master-promise-list)
         " SVG(s)."))
  (if (empty? @svg-fetch-master-promise-list)
    (p/resolved true) ; No SVGs to load
    (-> (p/all @svg-fetch-master-promise-list)
        (p/then (fn [results]
                  (js/console.log "[wait-for-preload] All SVG promises settled.")
                  results))
        (p/catch
          (fn [error]
            ;; p/all rejects with the first error. Individual errors are already
            ;; handled by load-icon macro's promise chain
            ;; (logged and ::error stored).
            (js/console.error
              "[wait-for-preload] One or more SVGs failed to load overall." error)
            ; Propagate so caller of wait-for-preload can catch
            (p/rejected error))))))

(defn icon
  "Render an SVG icon inside an [:i.icon ...].
  Can be called as:
  - `(icon svg)` = with just the SVG content
  - `(icon attrs svg)` = with attributes and SVG content"
  ([svg]
   (icon {} svg))
  ([attrs svg]
   [:i.icon
    (merge attrs
           {:dangerouslySetInnerHTML
            {:__html svg}})]))
