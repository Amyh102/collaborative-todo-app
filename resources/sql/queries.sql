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
