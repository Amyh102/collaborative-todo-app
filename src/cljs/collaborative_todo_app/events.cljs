(ns collaborative-todo-app.events
  (:require
    [cljs.spec.alpha :as s]
    [re-frame.core :refer [reg-sub subscribe reg-cofx reg-event-db reg-event-fx reg-fx inject-cofx after path dispatch]]
    [cljs.reader]
    [ajax.core :as ajax]
    [reitit.frontend.easy :as rfe]
    [reitit.frontend.controllers :as rfc]))


;; ----- Default DB ----------------------------------------------------------

(def default-db
  {:todos        (sorted-map)
   :showing-list false
   :showing      :all
   :logged-in    false
   :auth-error   nil})

;; -- Local Storage  ----------------------------------------------------------

(def ls-key "users-reframe")

(defn get-ls-db
  []
  (into (sorted-map)
        (some->> (.getItem js/localStorage ls-key)
                 (cljs.reader/read-string)
                 )))

(defn set-ls
  [ls]
  (.setItem js/localStorage ls-key (str ls)))

(defn todos->local-store
  [todos]
  (let [curr-ls (get-ls-db)]
    (if (:showing-list curr-ls)
      (set-ls
        (assoc-in curr-ls
                  [:todo-lists (:current-list curr-ls) :todos]
                  todos))
      curr-ls)))

(defn user->local-store
  [full_name username]
  (let [curr-ls (get-ls-db)]
    (set-ls (assoc-in curr-ls
                      [:users username]
                      {:full_name full_name
                       :subscriptions []}))))

(defn new-list->local-store
  [title id]
  (let [curr-ls (get-ls-db)]
    (set-ls (assoc-in curr-ls
                      [:todo-lists id]
                      {:title title :users [] :todos {}}))))

(defn login->local-store
  [username]
  (let [curr-ls (get-ls-db)]
    (set-ls
      (assoc (assoc curr-ls :logged-in true) :current-user username))))

(defn logout->local-store
  []
  (let [curr-ls (get-ls-db)]
    (set-ls
      (assoc (assoc (assoc (assoc curr-ls
                             :logged-in false)
                      :current-user nil)
               :showing-list false)
        :current-list nil))))

;; -- cofx Registrations  -----------------------------------------------------

(reg-cofx
  :local-store
  (fn [cofx _]
    (assoc cofx :local-store
                (get-ls-db))))

;; ----- Interceptors --------------------------------------------------

(def ->local-store (after todos->local-store))

(def todo-interceptors [(path :todos)
                        ->local-store])

;; -- Helpers -----------------------------------------------------------------

(defn allocate-next-id
  "Returns the next id of a map (todos or todo-lists).
  Assumes items are sorted.
  Returns one more than the current largest id."
  [map]
  ((fnil inc 0) (last (keys map))))


;; -- Event Handlers ----------------------------------------------------------


;; DB Initializer
(reg-event-fx
  :initialise-db
  [(inject-cofx :local-store)]
  (fn [{:keys [db local-store]} _]
    (if (:logged-in local-store)
      {:db {:todos        nil
            :showing-list false
            :showing      :all
            :logged-in    true
            :auth-error   nil}}
      {:db default-db})))

(reg-fx
  :ls
  (fn [default-ls]
    (set-ls default-ls)))

;; Handles the Login Event

(reg-fx
  :login-user
  (fn [username]
    (login->local-store username)))

(reg-event-fx
 :login
 (fn [{:keys [db]}
      [_ data]]
   {:http-xhrio {:method :get
                 :uri "http://localhost:3000/users/auth"
                 :params data
                 :timeout 5000
                 :format          (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [::success-login-result data]
                 :on-failure [::failure-login-result data]}}
   ))

(reg-event-fx
 ::success-login-result
 (fn [db [_ {:keys [username password]} {{:keys [response auth-error]} :response}]]
   {:db (assoc (assoc db :logged-in true) :auth-error nil)
    :login-user username}))

(reg-event-db
 ::failure-login-result
 (fn [db [_ {:keys [username password]} {{:keys [response auth-error]} :response}]]
   (assoc db :auth-error auth-error)))


;; Handles the SignIn Event

(reg-fx
  :new-user
  (fn [{:keys [full_name username password]}]
    (user->local-store full_name username)))

(reg-event-fx
 :signIn
 (fn [{:keys [db]}
      [_ data]]
   (println data)
   {:http-xhrio {:method :post
                 :uri "http://localhost:3000/users"
                 :params data
                 :timeout 5000
                 :format          (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [::success-signIn-result data]
                 :on-failure [::failure-signIn-result data]}}))

(reg-event-fx
 ::success-signIn-result
 (fn [db [_ {:keys [full_name username password] :as data} {{:keys [result]} :response} :as res]]
   (println res)
   {:db (assoc db :auth-error nil)
    :new-user data}))

(reg-event-db
 ::failure-signIn-result
 (fn [db [_ {:keys [full_name username password]} {{:keys [result]} :response} :as res]]
   (println res)
   (assoc db :auth-error result)))

;; Handles the LogOut Event

(reg-event-fx
  :logout
  (fn [{:keys [db]} _]
    {:db          (assoc (assoc (assoc db
                                  :logged-in false)
                           :todos (sorted-map))
                    :showing-list false)
     :logout-user nil}))

(reg-fx
  :logout-user
  (fn [_]
    (logout->local-store)))

;; Other Todo Add, Update and Delete Event Handlers

(reg-event-db
  :set-showing
  (fn [db [_ new-filter-kw]]
    (assoc db :showing new-filter-kw)))

(reg-event-db
  :clear-auth-error
  (fn [db _]
    (assoc db :auth-error nil)))

(reg-event-db
  :add-todo
  todo-interceptors
  (fn [todos [_ text]]
    (let [id (allocate-next-id todos)]
      (assoc todos id {:id id :title text :done false}))))

(reg-event-db
  :toggle-done
  todo-interceptors
  (fn [todos [_ id]]
    (update-in todos [id :done] not)))

(reg-event-db
  :save
  todo-interceptors
  (fn [todos [_ id title]]
    (assoc-in todos [id :title] title)))

(reg-event-db
  :delete-todo
  todo-interceptors
  (fn [todos [_ id]]
    (dissoc todos id)))

(reg-event-db
  :clear-completed
  todo-interceptors
  (fn [todos _]
    (let [done-ids (->> (vals todos)
                        (filter :done)
                        (map :id))]
      (reduce dissoc todos done-ids))))

(reg-event-db
  :complete-all-toggle
  todo-interceptors
  (fn [todos _]
    (let [new-done (not-every? :done (vals todos))]
      (reduce #(assoc-in %1 [%2 :done] new-done)
              todos
              (keys todos)))))

;; Event handlers for creating/subscribing to a new todo-list

(reg-event-fx
  :set-current-list
  [(inject-cofx :local-store)]
  (fn [{:keys [db local-store]} [_ id]]
    (if (nil? id)
      {:ls (assoc (assoc local-store :current-list id) :showing-list false)
       :db (assoc db :todos nil)}
      {:ls (assoc (assoc local-store :current-list id) :showing-list true)
       :db (assoc db :todos (:todos (get (:todo-lists local-store) id)))})))

(reg-event-fx
  :go-to-dashboard
  (fn []
    (dispatch [:set-current-list nil])))

(reg-fx
  :add-list-to-user
  (fn [id]
    (let [curr-ls (get-ls-db)]
      (set-ls (update-in curr-ls
                         [:users (:current-user curr-ls) :subscriptions]
                         #(conj % id))))))

(reg-fx
  :add-user-to-list
  (fn [id]
    (let [curr-ls (get-ls-db)]
      (set-ls (update-in curr-ls
                         [:todo-lists id :users]
                         #(conj % (:current-user curr-ls)))))))

(reg-event-fx
  :sub-to-todo-list
  [(inject-cofx :local-store)]
  (fn [{:keys [db local-store]} [_ id]]
    (let [todo-list (get (:todo-lists local-store) id)]
      (if todo-list
        (if (some #(= % id) (:subscriptions (get (:users local-store) (:current-user local-store))))
          (js/alert (str "You are already subscribed to the Todo List: " (:title todo-list)))
          (do (println "updated")
              {:add-list-to-user id
               :add-user-to-list id}))
        (do (js/alert "Code is invalid."
                      {}))))))

(reg-fx
  :new-list
  (fn [{:keys [title id]}]
    (new-list->local-store title id)))

(reg-event-fx
  :create-todo-list
  [(inject-cofx :local-store)]
  (fn [{:keys [db local-store]} [_ title]]
    (println title)
    {:http-xhrio {:method :post
                  :uri "http://localhost:3000/todo-list"
                  :params {:title title
                           :list_of_todos {}
                           :subscribed_users (vector (:current-user local-store))}
                  :timeout 5000
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::success-create-todo-list]
                  :on-failure [::failure-create-todo-list]}}))

(reg-event-fx
  ::success-create-todo-list
  (fn [db [_ res]]
    (dispatch [:set-current-list (:list_id res)])))

(reg-event-db
  ::failure-create-todo-list
  (fn [db [_ res]]
    (js/alert res)))


;; Subscriptions

(reg-sub
  :showing
  (fn [db _]
    (:showing db)))

(reg-sub
  :auth-error
  (fn [db _]
    (:auth-error db)))

(reg-sub
  :todos
  (fn [db _]
    (:todos db)))

(reg-sub
  :logged-in
  (fn [_ _]
    (let [curr-ls (get-ls-db)]
      (:logged-in curr-ls))))

(reg-sub
  :visible-todos
  (fn [_ _]
    [(subscribe [:todos])
     (subscribe [:showing])])
  (fn [[todos showing] _]
    (let [filter-fn (case showing
                      :active #(false? (:done (second %)))
                      :done #(:done (second %))
                      :all identity)]
      (filter filter-fn todos))))

(reg-sub
  :all-complete?
  :<- [:todos]
  (fn [todos _]
    (every? #(:done (second %)) todos)))

(reg-sub
  :completed-count
  :<- [:todos]
  (fn [todos _]
    (count (filter #(:done (second %)) todos))))

(reg-sub
  :footer-counts
  :<- [:todos]
  :<- [:completed-count]
  (fn [[todos completed] _]
    [(- (count todos) completed) completed]))

(reg-sub
  :subbed-lists
  (fn []
    (let [curr-ls (get-ls-db)]
      (:subscriptions (get (:users curr-ls) (:current-user curr-ls))))))

(reg-sub
  :all-todo-lists
  (fn []
    (let [curr-ls (get-ls-db)]
      (:todo-lists curr-ls))))

(reg-sub
  :current-list
  (fn []
    (let [curr-ls (get-ls-db)]
      (:title (get (:todo-lists curr-ls) (:current-list curr-ls))))))