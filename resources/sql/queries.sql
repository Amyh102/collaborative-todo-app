-- :name create-user! :! :n
-- :doc creates a new user record
INSERT INTO users
(full_name, username, password)
VALUES (:full_name, :username, :password)

-- :name get-user! :? :1
-- :doc retrieves a user record given the id
SELECT * FROM users
WHERE username = :username

-- :name delete-user! :! :n
-- :doc deletes a user record given the id
DELETE FROM users
WHERE username = :username

-- :name get-max-id! :? :1
-- :doc retrieves the maximum id in tasks. If empty returns 0
SELECT COALESCE (MAX (list_id), 0)
FROM tasks

-- :name create-list! :! :n
-- :doc creates a new todo list
INSERT INTO tasks
(list_id, title, list_of_todos, subscribed_users)
VALUES (:list_id, :title, :list_of_todos, :subscribed_users)

-- :name update-todos! :! :n
-- :doc updates todos in a todo list
UPDATE tasks
SET list_of_todos = :list_of_todos
WHERE list_id = :list_id

-- :name add-to-subscription-list! :! :n
-- :doc updates subscription list of a user
UPDATE users
SET subscriptions = ARRAY(SELECT DISTINCT UNNEST(ARRAY_APPEND(subscriptions, :subscription::int)))
WHERE username = :username

-- :name remove-from-subscription-list! :! :n
-- :doc updates subscription list of a user
UPDATE users
SET subscriptions = ARRAY_REMOVE(subscriptions, :subscription::int)
WHERE username = :username

-- :name add-to-subscribed-users! :! :n
-- :doc updates subscribed users in todo list
UPDATE tasks
SET subscribed_users = ARRAY(SELECT DISTINCT UNNEST(ARRAY_APPEND(subscribed_users, :subscribed_user::text)))
WHERE list_id = :list_id

-- :name remove-from-subscribed-users! :! :n
-- :doc updates subscribed users in todo list
UPDATE tasks
SET subscribed_users = ARRAY_REMOVE(subscribed_users, :subscribed_user::text)
WHERE list_id = :list_id