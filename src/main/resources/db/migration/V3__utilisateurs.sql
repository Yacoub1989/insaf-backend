
CREATE TABLE utilisateur (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,

    nom VARCHAR(150) NOT NULL,
    role VARCHAR(30) NOT NULL,

    militant_id BIGINT NULL,

    actif BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_utilisateur_militant
        FOREIGN KEY (militant_id)
        REFERENCES militant_reference(id),

    CONSTRAINT chk_utilisateur_role
        CHECK (role IN (
            'ADMIN',
            'MILITANT',
            'FINANCE',
            'CONTROLE'
        ))
);
