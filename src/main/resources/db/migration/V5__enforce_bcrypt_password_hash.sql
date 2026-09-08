UPDATE users
    SET password_hash = '$2a$10$' || substr(
            md5(gen_random_uuid()::text) || md5(gen_random_uuid()::text), 1, 53)
    WHERE password_hash !~ '^\$2[aby]\$[0-9]{2}\$[A-Za-z0-9./]{53}$';

ALTER TABLE users
    ADD CONSTRAINT ck_users_password_hash_is_bcrypt
        CHECK (password_hash ~ '^\$2[aby]\$[0-9]{2}\$[A-Za-z0-9./]{53}$');
