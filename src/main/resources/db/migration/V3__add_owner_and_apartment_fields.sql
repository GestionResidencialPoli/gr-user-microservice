ALTER TABLE users
    ADD COLUMN document_number VARCHAR(30);

UPDATE users
    SET document_number = 'PENDIENTE-' || id
    WHERE document_number IS NULL;

ALTER TABLE users
    ALTER COLUMN document_number SET NOT NULL,
    ADD CONSTRAINT uk_users_document_number UNIQUE (document_number),
    ADD CONSTRAINT ck_users_document_number_not_blank CHECK (btrim(document_number) <> '');

ALTER TABLE apartments
    ADD COLUMN coeficiente_copropiedad NUMERIC(6,4),
    ADD COLUMN area NUMERIC(8,2),
    ADD CONSTRAINT ck_apartments_coeficiente_copropiedad_range
        CHECK (coeficiente_copropiedad IS NULL OR (coeficiente_copropiedad > 0 AND coeficiente_copropiedad <= 1)),
    ADD CONSTRAINT ck_apartments_area_positive
        CHECK (area IS NULL OR area > 0);
