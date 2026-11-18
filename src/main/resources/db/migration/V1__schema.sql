CREATE TABLE militant_reference (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 crm_militant_id VARCHAR(100) NULL UNIQUE,
 nom VARCHAR(120) NOT NULL,
 prenom VARCHAR(120) NOT NULL,
 telephone VARCHAR(30),
 source VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE categorie_cotisation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(40) NOT NULL UNIQUE,
 libelle VARCHAR(150) NOT NULL,
 montant DECIMAL(15,2) NOT NULL,
 periodicite VARCHAR(20) NOT NULL,
 actif BOOLEAN NOT NULL DEFAULT TRUE,
 CONSTRAINT chk_categorie_montant CHECK (montant >= 0)
);
CREATE TABLE cotisation_echeance (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 militant_id BIGINT NOT NULL,
 categorie_id BIGINT NOT NULL,
 date_debut DATE NOT NULL,
 date_fin DATE NOT NULL,
 date_echeance DATE NOT NULL,
 montant_du DECIMAL(15,2) NOT NULL,
 montant_paye DECIMAL(15,2) NOT NULL DEFAULT 0,
 statut VARCHAR(25) NOT NULL DEFAULT 'EN_ATTENTE',
 CONSTRAINT fk_echeance_militant FOREIGN KEY (militant_id) REFERENCES militant_reference(id),
 CONSTRAINT fk_echeance_categorie FOREIGN KEY (categorie_id) REFERENCES categorie_cotisation(id),
 CONSTRAINT uq_echeance UNIQUE (militant_id,categorie_id,date_debut,date_fin),
 CONSTRAINT chk_echeance_montant CHECK (montant_du >= 0 AND montant_paye >= 0 AND montant_paye <= montant_du)
);
CREATE TABLE don (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 nom_donateur VARCHAR(200) NOT NULL,
 montant DECIMAL(15,2) NOT NULL,
 statut VARCHAR(30) NOT NULL DEFAULT 'EN_ATTENTE',
 description VARCHAR(1000),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT chk_don_montant CHECK (montant > 0)
);
CREATE TABLE paiement (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 reference VARCHAR(100) NOT NULL UNIQUE,
 montant DECIMAL(15,2) NOT NULL,
 mode VARCHAR(30) NOT NULL,
 statut VARCHAR(30) NOT NULL DEFAULT 'INITIE',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT chk_paiement_montant CHECK (montant > 0)
);
