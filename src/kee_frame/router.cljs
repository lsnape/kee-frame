(ns kee-frame.router
  (:require [re-frame.core :as rf]
            [kee-frame.state :as state]
            [kee-frame.controller :as controller]
            [accountant.core :as accountant]
            [bidi.bidi :as bidi]
            [reagent.core :as reagent]))

(defn url [& params]
  (when-not @state/routes
    (throw (ex-info "No routes defined for this app" {:routes @state/routes})))
  (apply bidi/path-for @state/routes params))

(defn goto [route & params]
  (accountant/navigate! (apply url route params)))

(defn nav-handler [process-route]
  (fn [path]
    (if-let [route (->> path
                        (bidi/match-route @state/routes)
                        process-route)]
      (rf/dispatch [::route-changed route])
      (do (rf/console :group "No route match found")
          (rf/console :error "No match found for path " path)
          (rf/console :log "Available routes: " @state/routes)
          (rf/console :groupEnd)))))

(defn bootstrap-routes [routes process-route]
  (let [initialized? (boolean @state/routes)]
    (reset! state/routes routes)
    (rf/reg-fx :navigate-to #(apply goto %))

    (when-not initialized?
      (accountant/configure-navigation!
        {:nav-handler  (nav-handler process-route)
         :path-exists? #(boolean (bidi/match-route @state/routes %))}))
    (accountant/dispatch-current!)))

(rf/reg-event-db :init (fn [db [_ initial]] (merge initial db)))

(defn reg-route-event []
  (rf/reg-event-fx ::route-changed
                   (if @state/debug? [rf/debug])
                   (fn [{:keys [db] :as ctx} [_ route]]
                     (swap! state/controllers controller/apply-route ctx route)
                     {:db (assoc db :kee-frame/route route)})))

(defn start! [{:keys [routes initial-db process-route app-db-spec debug? root-component]
               :or   {process-route identity
                      debug?        false}}]
  (reset! state/app-db-spec app-db-spec)
  (reset! state/debug? debug?)
  (when routes
    (bootstrap-routes routes process-route))

  (when initial-db
    (rf/dispatch-sync [:init initial-db]))

  (reg-route-event)

  (rf/reg-sub :kee-frame/route (fn [db] (:kee-frame/route db nil)))

  (when root-component
    (if-let [app-element (.getElementById js/document "app")]
      (reagent/render root-component
                      app-element)
      (throw (ex-info "Could not find element with id 'app' to mount app into" {:component root-component})))))

(defn make-route-component [component route]
  (if (fn? component)
    [component route]
    component))

(defn switch-route [f & pairs]
  (when-not (= 0 (mod (count pairs) 2))
    (throw (ex-info "switch-route accepts an even number of args" {:pairs       pairs
                                                                   :pairs-count (count pairs)})))
  (let [route (rf/subscribe [:kee-frame/route])
        dispatch-value (f @route)]
    (loop [[first-pair & rest-pairs] (partition 2 pairs)]
      (if first-pair
        (let [[value component] first-pair]
          (if (= value dispatch-value)
            (make-route-component component @route)
            (recur rest-pairs)))
        (throw (ex-info "Could not find a component to match route" {:route          @route
                                                                     :dispatch-value dispatch-value
                                                                     :pairs          pairs}))))))