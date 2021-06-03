(ns collaborative-todo-app.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [collaborative-todo-app.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[collaborative-todo-app started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[collaborative-todo-app has shut down successfully]=-"))
   :middleware wrap-dev})
