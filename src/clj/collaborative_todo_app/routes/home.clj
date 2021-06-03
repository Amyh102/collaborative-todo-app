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
   ["/users" {:get {:summary "get user info"
                    :parameters {:body-params {:username string?}}
                    :handler (fn [{{:keys [username]} :body-params :as req}]
                               (println "This FIred")
                               {:status 200
                                :body (db/get-user! {:username username})})}
              :post {:middleware [(fn [handler]
                                    (fn [{{:keys [username password]} :body-params :as req}]
                                      (let [already-exists? (db/get-user! {:username username})]
                                        (if already-exists?
                                          {:status 400
                                           :body "Username already exists"}
                                          (handler req)))))]
                     :summary "creates a user"
                     :parameters {:body-params {:username string? :password string?}}
                     :handler (fn [{{:keys [username password]} :body-params :as req}]
                                (println username)
                                (println password)
                                (println req)
                                (db/create-user! {:username username :password password})
                                {:status 200
                                 :body "Created Successfully"})}}]
   ["/auth" {:get {:middleware [(fn [handler]
                                    (fn [{{:keys [username password]} :body-params :as req}]
                                      (let [user-info (db/get-user! {:username username})]
                                        (if (not user-info)
                                          {:status 400
                                           :body {:response false
                                                  :auth-error "Username doesn't exist"}}
                                          (handler (assoc-in req [:user-info] user-info))))))]
                   :summary "check login info"
                   :parameters {:body-params {:username string? :password string?}}
                   :handler (fn [{{:keys [username password]} :body-params :as req}]
                              (if (= (get-in req [:user-info :password]) password)
                                {:status 200
                                 :body {:response true
                                        :auth-error nil}}
                                {:status 400
                                 :body {:response false
                                        :auth-error "Password is incorrect"}}))}}]])

