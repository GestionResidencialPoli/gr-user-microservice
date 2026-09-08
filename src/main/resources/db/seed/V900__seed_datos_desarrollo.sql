INSERT INTO users (first_name, last_name, document_number, email, password_hash, phone, status) VALUES
    ('Camila', 'Restrepo', 'CC-1000000001', 'residente.demo@gestionresidencial.test', '$2a$10$vCfkisbGWQlH6MvNn9ZZzuWqcY.brPoMoPfJfsdPpZTr9.uIQRC/C', '3000000001', 'ACTIVE'),
    ('Julian', 'Ospina', 'CC-1000000002', 'vigilante.demo@gestionresidencial.test', '$2a$10$vCfkisbGWQlH6MvNn9ZZzuWqcY.brPoMoPfJfsdPpZTr9.uIQRC/C', '3000000002', 'ACTIVE'),
    ('Valentina', 'Gomez', 'CC-1000000003', 'admin.demo@gestionresidencial.test', '$2a$10$vCfkisbGWQlH6MvNn9ZZzuWqcY.brPoMoPfJfsdPpZTr9.uIQRC/C', '3000000003', 'ACTIVE');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE (u.email, r.name) IN (
    ('residente.demo@gestionresidencial.test', 'RESIDENTE'),
    ('vigilante.demo@gestionresidencial.test', 'VIGILANTE'),
    ('admin.demo@gestionresidencial.test', 'ADMINISTRACION')
);

INSERT INTO users (first_name, last_name, document_number, email, password_hash, status) VALUES
    ('Andres', 'Zapata', 'CC-2000000001', 'owner1@gestionresidencial.test', 'PENDING_ACTIVATION', 'ACTIVE'),
    ('Laura', 'Munoz', 'CC-2000000002', 'owner2@gestionresidencial.test', 'PENDING_ACTIVATION', 'ACTIVE'),
    ('Felipe', 'Cardona', 'CC-2000000003', 'owner3@gestionresidencial.test', 'PENDING_ACTIVATION', 'ACTIVE'),
    ('Manuela', 'Velez', 'CC-2000000004', 'owner4@gestionresidencial.test', 'PENDING_ACTIVATION', 'ACTIVE'),
    ('Sebastian', 'Giraldo', 'CC-2000000005', 'owner5@gestionresidencial.test', 'PENDING_ACTIVATION', 'ACTIVE'),
    ('Isabella', 'Correa', 'CC-2000000006', 'owner6@gestionresidencial.test', 'PENDING_ACTIVATION', 'ACTIVE'),
    ('Mateo', 'Salazar', 'CC-2000000007', 'owner7@gestionresidencial.test', 'PENDING_ACTIVATION', 'ACTIVE'),
    ('Daniela', 'Herrera', 'CC-2000000008', 'owner8@gestionresidencial.test', 'PENDING_ACTIVATION', 'ACTIVE'),
    ('Santiago', 'Perez', 'CC-2000000009', 'owner9@gestionresidencial.test', 'PENDING_ACTIVATION', 'ACTIVE'),
    ('Juan', 'Arias', 'CC-3000000001', 'tenant1@gestionresidencial.test', 'PENDING_ACTIVATION', 'ACTIVE'),
    ('Paula', 'Londono', 'CC-3000000002', 'tenant2@gestionresidencial.test', 'PENDING_ACTIVATION', 'ACTIVE'),
    ('Cristian', 'Rios', 'CC-3000000003', 'tenant3@gestionresidencial.test', 'PENDING_ACTIVATION', 'ACTIVE');

INSERT INTO apartments (torre, numero, piso, activo, coeficiente_copropiedad, area) VALUES
    ('A', '101', 1, TRUE, 0.0450, 65.50),
    ('A', '102', 1, TRUE, 0.0450, 65.50),
    ('A', '201', 2, TRUE, 0.0480, 70.00),
    ('A', '202', 2, TRUE, 0.0480, 70.00),
    ('A', '301', 3, TRUE, 0.0520, 78.00),
    ('B', '101', 1, TRUE, 0.0450, 65.50),
    ('B', '102', 1, TRUE, 0.0450, 65.50),
    ('B', '201', 2, TRUE, 0.0480, 70.00),
    ('B', '202', 2, TRUE, 0.0480, 70.00),
    ('B', '301', 3, TRUE, 0.0520, 78.00);

INSERT INTO owners (user_id, apartment_id, principal)
SELECT u.id, a.id, TRUE
FROM (VALUES
    ('residente.demo@gestionresidencial.test', 'A', '101'),
    ('owner1@gestionresidencial.test', 'A', '102'),
    ('owner2@gestionresidencial.test', 'A', '201'),
    ('owner3@gestionresidencial.test', 'A', '202'),
    ('owner4@gestionresidencial.test', 'A', '301'),
    ('owner5@gestionresidencial.test', 'B', '101'),
    ('owner6@gestionresidencial.test', 'B', '102'),
    ('owner7@gestionresidencial.test', 'B', '201'),
    ('owner8@gestionresidencial.test', 'B', '202'),
    ('owner9@gestionresidencial.test', 'B', '301')
) AS assignment(email, torre, numero)
JOIN users u ON u.email = assignment.email
JOIN apartments a ON a.torre = assignment.torre AND a.numero = assignment.numero;

INSERT INTO tenants (user_id, apartment_id, start_date, end_date)
SELECT u.id, a.id, assignment.start_date, NULL
FROM (VALUES
    ('tenant1@gestionresidencial.test', 'B', '101', DATE '2025-03-01'),
    ('tenant2@gestionresidencial.test', 'B', '201', DATE '2025-06-01'),
    ('tenant3@gestionresidencial.test', 'B', '301', DATE '2025-08-15')
) AS assignment(email, torre, numero, start_date)
JOIN users u ON u.email = assignment.email
JOIN apartments a ON a.torre = assignment.torre AND a.numero = assignment.numero;
