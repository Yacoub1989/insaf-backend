CREATE DATABASE IF NOT EXISTS insaf_finance CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Exécuter avec un compte administrateur MySQL ; remplacer le mot de passe.
CREATE USER IF NOT EXISTS 'insaf_app'@'localhost' IDENTIFIED BY 'CHANGE_THIS_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON insaf_finance.* TO 'insaf_app'@'localhost';
-- En production, séparer l'utilisateur Flyway (DDL) de l'utilisateur applicatif (DML).
