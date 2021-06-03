DO
$$
    BEGIN
        CREATE TABLE users (
            username VARCHAR ( 50 ) UNIQUE NOT NULL,
            password VARCHAR ( 50 ) NOT NULL,
            subscriptions integer[]
        );
        
        CREATE TABLE tasks (
            list_id serial PRIMARY KEY,
            title  VARCHAR NOT NULL,
            list_of_todos jsonb,
            subscribed_users integer[]
        );
    END
$$;