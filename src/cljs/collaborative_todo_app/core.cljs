(ns collaborative-todo-app.core
  (:require
    [day8.re-frame.http-fx]
    [reagent.dom :as rdom]
    [reagent.core :as reagent]
    [re-frame.core :refer [dispatch subscribe clear-subscription-cache! dispatch-sync]]
    [goog.events :as events]
    [goog.history.EventType :as HistoryEventType]
    [markdown.core :refer [md->html]]
    [collaborative-todo-app.ajax :as ajax]
    [collaborative-todo-app.events]
    [reitit.core :as reitit]
    [reitit.frontend.easy :as rfe]
    [clojure.string :as string])
  (:import goog.History))

(defn todo-input [{:keys [title on-save on-stop]}]
  (let [val  (reagent/atom title)
        stop #(do (reset! val "")
                  (when on-stop (on-stop)))
        save #(let [v (-> @val str string/trim)]
                (on-save v)
                (stop))]
    (fn [props]
      [:input (merge (dissoc props :on-save :on-stop :title)
                     {:type        "text"
                      :value       @val
                      :auto-focus  true
                      :on-blur     save
                      :on-change   #(reset! val (-> % .-target .-value))
                      :on-key-down #(case (.-which %)
                                      13 (save)
                                      27 (stop)
                                      nil)})])))


(defn todo-item
  []
  (let [editing (reagent/atom false)]
    (fn [{:keys [id title done]}]
      [:li {:class (str (when done "completed ")
                        (when @editing "editing"))}
       [:div.view
        [:input.toggle
         {:type "checkbox"
          :checked done
          :on-change #(dispatch [:toggle-done id])}]
        [:label
         {:on-double-click #(reset! editing true)}
         title]
        [:button.destroy
         {:on-click #(dispatch [:delete-todo id])}]]
       (when @editing
         [todo-input
          {:class "edit"
           :title title
           :on-save #(if (seq %)
                       (dispatch [:save id %])
                       (dispatch [:delete-todo id]))
           :on-stop #(reset! editing false)}])])))


(defn task-list
  []
  (let [visible-todos @(subscribe [:visible-todos])
        all-complete? @(subscribe [:all-complete?])]
    [:section#main
     [:input#toggle-all
      {:type "checkbox"
       :checked all-complete?
       :on-change #(dispatch [:complete-all-toggle])}]
     [:label
      {:for "toggle-all"}
      "Mark all as complete"]
     [:ul#todo-list
      (for [todo  visible-todos]
        ^{:key (first todo)} [todo-item (second todo)])]]))


(defn footer-controls
  []
  (let [[active done] @(subscribe [:footer-counts])
        showing       @(subscribe [:showing])
        a-fn          (fn [filter-kw txt]
                        [:a {:class (when (= filter-kw showing) "selected")
                             :on-click #(dispatch [:set-showing filter-kw])} txt])]
    [:footer#footer
     [:span#todo-count
      [:strong active] " " (case active 1 "item" "items") " left"]
     [:ul#filters
      [:li (a-fn :all    "All")]
      [:li (a-fn :active "Active")]
      [:li (a-fn :done   "Completed")]]
     (when (pos? done)
       [:button#clear-completed {:on-click #(dispatch [:clear-completed])}
        "Clear completed"])]))


(defn task-entry
  []
  [:header#header
   [:h1 "todos"]
   [todo-input
    {:id "new-todo"
     :placeholder "What needs to be done?"
     :on-save #(when (seq %)
                 (dispatch [:add-todo %]))}]])


(defn todo-app
  []
  [:<>
   [:button#logout_button {:on-click #(dispatch [:logout nil])} "Log Out"]
   [:section#todoapp
    [task-entry]
    (when (seq @(subscribe [:todos]))
      [task-list])
    [footer-controls]]
   [:footer#info
    [:p "Double-click to edit a todo"]]])

(defn input-element
  "An input element which updates its value on change"
  [id name type atom-value]
  [:input {:id id
           :name name
           :type type
           :required true
           :value @atom-value
           :on-change (fn [x] (reset! atom-value (.. x -target -value)))}])

(defn signIn
  [form]
  (let [auth-error @(subscribe [:auth-error])
        name (reagent/atom nil)
        username (reagent/atom nil)
        password (reagent/atom nil)]
    [:div#signup
     [:h1 "Sign Up For the TodoApp"]
     [:form
      [:div.field-wrap
       [:label "Full Name"]
       [input-element "name" "name" "text" name]]
      [:div.field-wrap
       [:label "Username"]
       [input-element "username" "username" "text" username]]
      [:div.field-wrap
       [:label "Password"]
       [input-element "password" "password" "text" password]]]
     (when auth-error [:div#auth-error auth-error])
     [:div.buttons
      [:button {:class "button button-block"
                :on-click #((dispatch [:signIn {:name @name
                                                :username @username
                                                :password @password}]))}
       "Get Started"]
      [:button {:class "button button-block"
                :on-click #((reset! form false)
                            (dispatch [:clear-auth-error nil]))}
       "Log In"]]]))

(defn logIn
  [form]
  (let [auth-error @(subscribe [:auth-error])
        username (reagent/atom nil)
        password (reagent/atom nil)]
    [:div#login
     [:h1 "LogIn to your TodoApp Account"]
     [:form
      [:div.field-wrap
       [:label "Username"]
       [input-element "username" "username" "text" username]]
      [:div.field-wrap
       [:label "Password"]
       [input-element "password" "password" "text" password]]]
     (when auth-error [:div#auth-error auth-error])
     [:div.buttons
      [:button {:type "submit"
                :class "button button-block"
                :on-click #((dispatch [:login {:username @username
                                              :password @password}])
                            (dispatch [:clear-auth-error nil]))}
       "LogIn"]
      [:button {:class "button button-block"
                :on-click #((reset! form true)
                            (dispatch [:clear-auth-error nil]))}
       "Sign Up"]]]))



(defn auth
  [form]
    [:div.form
     [:div.tab-content
      (if @form
        [signIn form]
        [logIn form])]])

(defn main
  []
  (let [loggedIn @(subscribe [:logged-in])
        form (reagent/atom false)]
    (if loggedIn
      [todo-app]
      [auth form])))

;; (def router
;;   (reitit/router
;;     [["/" {:name        :main
;;            :view        #'main}]]))

;; (defn start-router! []
;;   (rfe/start!
;;     router
;;     {}))

;; -------------------------

;; Enabling Console Log
(enable-console-print!)

(dispatch-sync [:initialise-db])

;; Initialize app
(defn ^:dev/after-load mount-components []
  (clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [#'main] root-el)))

(defn init! []
  (ajax/load-interceptors!)
  (mount-components))
