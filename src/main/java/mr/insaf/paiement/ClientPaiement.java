package mr.insaf.paiement;

import java.math.BigDecimal;

/** Contrat côté INSAF. Les formats et transports officiels restent propres à chaque implémentation. */
public interface ClientPaiement {
    record Demande(String reference, BigDecimal montant, String devise) {}

    record Resultat(OperateurPaiement operateur, String reference, String referenceOperateur,
                    BigDecimal montant, String devise, StatutPaiement statut) {}

    Resultat initier(Demande demande);

    Resultat verifier(Demande demande, String referenceOperateur);
}
