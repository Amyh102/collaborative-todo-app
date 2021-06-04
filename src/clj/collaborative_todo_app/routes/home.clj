(ns collaborative-todo-app.routes.home
  (:require
    [collaborative-todo-app.layout :as layout]
    [clojure.java.io :as io]
    [collaborative-todo-app.db.core :as db]
    [collaborative-todo-app.middleware :as middleware]
    [ring.util.response]
    [ring.util.http-response :as response]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn home-routes []
  [""
   {:middleware [;;middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/users" {:get  {:summary    "get user info"
                     :parameters {:body-params {:username string?}}
                     :handler    (fn [{{:keys [username]} :body-params :as req}]
                                   {:status 200
                                    :body   (db/get-user! {:username username})})}
              :post {:middleware [(fn [handler]
                                    (fn [{{:keys [full_name username password]} :body-params :as req}]
                                      (let [already-exists? (db/get-user! {:username username})]
                                        (if already-exists?
                                          {:status 400
                                           :body   {:result "Username already exists"}}
                                          (handler req)))))]
                     :summary    "creates a user"
                     :parameters {:body-params {:full_name string? :username string? :password string?}}
                     :handler    (fn [{{:keys [full_name username password]} :body-params :as req}]
                                   ;; (println req)
                                   (db/create-user! {:full_name full_name :username username :password password})
                                   {:status 200
                                    :body   {:result "Created Successfully"}})}}]
   ["/users/subscriptions" {:put {:summary    "updates subscription list of a user"
                                  :parameters {:body-params {:username string? :subscription integer? :add boolean?}}
                                  :handler    (fn [{{:keys [username subscription add]} :body-params :as req}]
                                                (let [todo-list (db/get-list! {:list_id subscription})
                                                      user-subs (:subscriptions (db/get-user! {:username username}))]
                                                  (if add
                                                    (if todo-list
                                                      (if (some #(= subscription %) user-subs)
                                                        {:status 400
                                                         :body   {:response false
                                                                  :message  (str "You are already subscribed to "
                                                                                 (:title todo-list))}}
                                                        (do (db/add-to-subscription-list! {:subscription subscription
                                                                                           :username     username})
                                                            {:status 200
                                                             :body   {:response true
                                                                      :message  (str "Successfully subscribed to "
                                                                                     (:title todo-list))}}))
                                                      {:status 400
                                                       :body   {:response false
                                                                :message  "The given code does not exist"}})
                                                    (do
                                                      (db/remove-from-subscription-list! {:subscription subscription
                                                                                          :username     username})
                                                      {:status 200
                                                       :body   {:response true
                                                                :message  "removed"}}))))}}]
   ["/users/auth" {:get {:middleware [(fn [handler]
                                        (fn [{{:keys [username password]} :params :as req}]
                                          (let [user-info (db/get-user! {:username username})]
                                            (if (not user-info)
                                              {:status 400
                                               :body   {:response   false
                                                        :auth-error "Username doesn't exist"}}
                                              (handler (assoc-in req [:user-info] user-info))))))]
                         :summary    "check login info"
                         :parameters {:params {:username string? :password string?}}
                         :handler    (fn [{{:keys [username password]} :params :as req}]
                                       (if (= (get-in req [:user-info :password]) password)
                                         {:status 200
                                          :body   {:response   true
                                                   :auth-error nil}}
                                         {:status 400
                                          :body   {:response   false
                                                   :auth-error "Password is incorrect"}}))}}]
   ["/todo-list" {:post {:middleware [(fn [handler]
                                        (fn [{{:keys [title list_of_todos subscribed_users]} :body-params :as req}]
                                          (let [list_id (inc (:coalesce (db/get-max-id!)))]
                                            (handler (assoc-in req [:body-params :list_id] list_id)))))]
                         :summary    "create a todo list"
                         :parameters {:body-params {:title            string?
                                                    :list_of_todos    map?
                                                    :subscribed_users vector?
                                                    :username         string?}}
                         :handler    (fn [{{:keys [list_id title list_of_todos subscribed_users username]}
                                           :body-params :as req}]
                                       (db/create-list! {:list_id          list_id
                                                         :title            title
                                                         :list_of_todos    list_of_todos
                                                         :subscribed_users subscribed_users})
                                       (db/add-to-subscription-list! {:subscription list_id
                                                                      :username     username})
                                       {:status 200
                                        :body   {:response true
                                                 :list_id  list_id}})}
                  :put  {:summary "updates todos of a todo list"
                         :handler (fn [{{:keys [list_id list_of_todos]} :body-params :as req}]
                                    (db/update-todos! {:list_id       list_id
                                                       :list_of_todos list_of_todos})
                                    {:status 200})}}]
   ["/todo-list/subscribed-users" {:put {:summary    "updates the subscribed users list of each todo list"
                                         :parameters {:body-params {:list_id integer? :subscribed_user string? :add boolean?}}
                                         :handler    (fn [{{:keys [list_id subscribed_user add]} :body-params :as req}]
                                                       (if add
                                                         (db/add-to-subscribed-users! {:subscribed_user subscribed_user
                                                                                       :list_id         list_id})
                                                         (db/remove-from-subscribed-users! {:subscribed_user subscribed_user
                                                                                            :list_id         list_id}))
                                                       {:status 200})}}]])

