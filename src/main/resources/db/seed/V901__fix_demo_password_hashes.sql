UPDATE users
SET password_hash = '$2b$10$O/6.lrkXSJYZGJGQ.MIfIOXyWqOk1HCfvoAJZAKAx0g6AcjJq3D1i'
WHERE email IN (
    'residente.demo@gestionresidencial.test',
    'vigilante.demo@gestionresidencial.test',
    'admin.demo@gestionresidencial.test'
)
AND status = 'ACTIVE';
