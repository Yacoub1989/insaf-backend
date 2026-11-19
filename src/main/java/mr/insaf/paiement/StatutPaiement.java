package mr.insaf.paiement;

public enum StatutPaiement {
    INITIE, EN_ATTENTE, CONFIRME, ECHOUE, ANNULE;

    boolean reserve() {
        return this == INITIE || this == EN_ATTENTE;
    }
}
