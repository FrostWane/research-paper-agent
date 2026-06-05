UPDATE users
SET role = 'ADMIN'
WHERE lower(username) = 'demo_user';
