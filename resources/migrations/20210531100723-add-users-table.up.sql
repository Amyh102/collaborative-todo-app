DO
$$
    BEGIN
        CREATE TABLE users (
            full_name VARCHAR ( 50 ) NOT NULL,
            username VARCHAR ( 50 ) UNIQUE NOT NULL,
            password VARCHAR ( 50 ) NOT NULL,
            subscriptions integer[]
        );
        
        CREATE TABLE tasks (
            list_id integer PRIMARY KEY NOT NULL UNIQUE,
            title  VARCHAR NOT NULL,
            list_of_todos jsonb,
            subscribed_users text[]
        );
    END
$$;