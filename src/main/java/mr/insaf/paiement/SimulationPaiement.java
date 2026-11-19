package mr.insaf.paiement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile(ProtectionSimulation.PROFIL)
@ConditionalOnProperty(name = ProtectionSimulation.DRAPEAU, havingValue = "true")
class SimulationPaiement {
    @Bean BankilyClient bankilyClient() { return new BankilySimule(); }
    @Bean SedadClient sedadClient() { return new SedadSimule(); }
    @Bean MasrviClient masrviClient() { return new MasrviSimule(); }

    abstract static class Simulateur implements ClientPaiement {
        private final OperateurPaiement operateur;

        Simulateur(OperateurPaiement operateur) {
            this.operateur = operateur;
        }

        @Override
        public Resultat initier(Demande demande) {
            return resultat(demande, StatutPaiement.EN_ATTENTE);
        }

        @Override
        public Resultat verifier(Demande demande, String referenceOperateur) {
            var resultat = resultat(demande, StatutPaiement.EN_ATTENTE);
            if (!resultat.referenceOperateur().equals(referenceOperateur)) {
                throw new IllegalArgumentException("Référence de simulation inconnue");
            }
            return resultat;
        }

        Resultat resultat(Demande demande, StatutPaiement statut) {
            return new Resultat(operateur, demande.reference(), "SIM-" + operateur + "-" + demande.reference(),
                    demande.montant(), demande.devise(), statut);
        }
    }

    private static final class BankilySimule extends Simulateur implements BankilyClient {
        BankilySimule() { super(OperateurPaiement.BANKILY); }
    }

    private static final class SedadSimule extends Simulateur implements SedadClient {
        SedadSimule() { super(OperateurPaiement.SEDAD); }
    }

    private static final class MasrviSimule extends Simulateur implements MasrviClient {
        MasrviSimule() { super(OperateurPaiement.MASRVI); }
    }
}
