ALTER TABLE cotisation_echeance
    ADD CONSTRAINT uq_echeance_id_militant UNIQUE (id, militant_id);

CREATE TABLE transaction_paiement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference VARCHAR(36) NOT NULL UNIQUE,
    militant_id BIGINT NOT NULL,
    echeance_id BIGINT NOT NULL,
    montant DECIMAL(15,2) NOT NULL,
    devise VARCHAR(3) NOT NULL,
    operateur VARCHAR(16) NOT NULL,
    reference_operateur VARCHAR(100),
    statut VARCHAR(16) NOT NULL,
    cle_idempotence VARCHAR(36) NOT NULL,
    simule BOOLEAN NOT NULL,
    motif_rapprochement VARCHAR(100),
    cree_le DATETIME(6) NOT NULL,
    modifie_le DATETIME(6) NOT NULL,
    confirme_le DATETIME(6),
    CONSTRAINT fk_paiement_echeance_militant FOREIGN KEY (echeance_id, militant_id)
        REFERENCES cotisation_echeance(id, militant_id),
    CONSTRAINT uq_paiement_idempotence UNIQUE (militant_id, cle_idempotence),
    CONSTRAINT uq_paiement_operateur UNIQUE (operateur, reference_operateur),
    CONSTRAINT chk_transaction_montant CHECK (montant > 0 AND devise = 'MRU'),
    CONSTRAINT chk_transaction_operateur CHECK (operateur IN ('BANKILY', 'SEDAD', 'MASRVI')),
    CONSTRAINT chk_transaction_statut CHECK (statut IN ('INITIE', 'EN_ATTENTE', 'CONFIRME', 'ECHOUE', 'ANNULE')),
    INDEX ix_paiement_reservations (echeance_id, statut),
    INDEX ix_paiement_historique (militant_id, cree_le, id)
);
