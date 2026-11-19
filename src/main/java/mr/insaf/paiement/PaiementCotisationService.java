package mr.insaf.paiement;

import mr.insaf.auth.CurrentUserService;
import mr.insaf.repository.CotisationEcheanceRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static mr.insaf.paiement.StatutPaiement.*;

@Service
public class PaiementCotisationService {
    private final CurrentUserService currentUser;
    private final CotisationEcheanceRepository echeances;
    private final TransactionPaiementRepository paiements;
    private final TransactionTemplate transaction;
    private final Map<OperateurPaiement, ClientPaiement> clients = new EnumMap<>(OperateurPaiement.class);

    public PaiementCotisationService(CurrentUserService currentUser, CotisationEcheanceRepository echeances,
                                      TransactionPaiementRepository paiements, PlatformTransactionManager transactions,
                                      ObjectProvider<BankilyClient> bankily, ObjectProvider<SedadClient> sedad,
                                      ObjectProvider<MasrviClient> masrvi) {
        this.currentUser = currentUser;
        this.echeances = echeances;
        this.paiements = paiements;
        this.transaction = new TransactionTemplate(transactions);
        bankily.ifAvailable(client -> clients.put(OperateurPaiement.BANKILY, client));
        sedad.ifAvailable(client -> clients.put(OperateurPaiement.SEDAD, client));
        masrvi.ifAvailable(client -> clients.put(OperateurPaiement.MASRVI, client));
    }

    public record Disponibilite(OperateurPaiement operateur, boolean disponible, boolean simulation) {}

    public record Vue(String reference, Long echeanceId, BigDecimal montant, String devise,
                      OperateurPaiement operateur, StatutPaiement statut, boolean simulation,
                      boolean rapprochement, Instant creeLe, Instant confirmeLe) {}

    public record Historique(List<Vue> paiements, int page, boolean pageSuivante) {}

    public List<Disponibilite> operateurs() {
        militant();
        return java.util.Arrays.stream(OperateurPaiement.values())
                .map(operateur -> {
                    boolean simule = clients.get(operateur) instanceof SimulationPaiement.Simulateur;
                    return new Disponibilite(operateur, simule, simule);
                }).toList();
    }

    public Vue initier(Long echeanceId, OperateurPaiement operateur, BigDecimal montant, UUID idempotence) {
        Long militantId = militant();
        if (montant.signum() <= 0 || montant.scale() > 2 || montant.precision() - montant.scale() > 13) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Montant MRU invalide");
        }
        var client = clients.get(operateur);
        // Official clients require their own activation and verified reconciliation workflow.
        if (!(client instanceof SimulationPaiement.Simulateur)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Opérateur indisponible");
        }

        String cle = idempotence.toString();
        TransactionPaiement paiement;
        try {
            paiement = transaction.execute(status -> {
                var echeance = echeances.verrouiller(echeanceId, militantId).orElseThrow(PaiementCotisationService::introuvable);
                var deja = paiements.findByMilitantIdAndCleIdempotence(militantId, cle);
                if (deja.isPresent()) return verifierDemande(deja.get(), echeanceId, operateur, montant);

                var reserve = paiements.totalReserve(echeanceId, List.of(INITIE, EN_ATTENTE));
                var disponible = echeance.montantDu.subtract(echeance.montantPaye)
                        .subtract(reserve == null ? BigDecimal.ZERO : reserve);
                if (montant.compareTo(disponible) > 0) throw conflit("Montant supérieur au solde disponible");

                var nouveau = new TransactionPaiement();
                nouveau.reference = UUID.randomUUID().toString();
                nouveau.militantId = militantId;
                nouveau.echeanceId = echeanceId;
                nouveau.montant = montant;
                nouveau.devise = "MRU";
                nouveau.operateur = operateur;
                nouveau.statut = INITIE;
                nouveau.simule = true;
                nouveau.cleIdempotence = cle;
                nouveau.creeLe = nouveau.modifieLe = Instant.now();
                return paiements.saveAndFlush(nouveau);
            });
        } catch (DataIntegrityViolationException error) {
            if (!(error.getMostSpecificCause() instanceof SQLException sql) || sql.getErrorCode() != 1062) throw error;
            // The same key can race across two different locked échéances.
            paiement = verifierDemande(paiements.findByMilitantIdAndCleIdempotence(militantId, cle)
                    .orElseThrow(() -> error), echeanceId, operateur, montant);
        }
        if (paiement.statut != INITIE) return vue(paiement);
        return appliquer(paiement, client.initier(demande(paiement)));
    }

    public Vue consulter(String reference) {
        return vue(paiementPersonnel(reference));
    }

    public Historique historique(Long echeanceId, int numero, int taille) {
        Long militantId = militant();
        var page = PageRequest.of(numero, taille, Sort.by(Sort.Direction.DESC, "creeLe", "id"));
        var resultat = echeanceId == null ? paiements.findByMilitantId(militantId, page)
                : paiements.findByMilitantIdAndEcheanceId(militantId, echeanceId, page);
        return new Historique(resultat.getContent().stream().map(PaiementCotisationService::vue).toList(),
                numero, resultat.hasNext());
    }

    Vue simuler(String reference, StatutPaiement statut) {
        var paiement = paiementPersonnel(reference);
        if (!paiement.simule || !(clients.get(paiement.operateur) instanceof SimulationPaiement.Simulateur simulateur)) {
            throw introuvable();
        }
        return appliquer(paiement, simulateur.resultat(demande(paiement), statut));
    }

    Vue appliquer(TransactionPaiement paiement, ClientPaiement.Resultat preuve) {
        return transaction.execute(status -> {
            var echeance = echeances.verrouiller(paiement.echeanceId, paiement.militantId)
                    .orElseThrow(PaiementCotisationService::introuvable);
            var courant = paiements.verrouiller(paiement.reference, paiement.militantId)
                    .orElseThrow(PaiementCotisationService::introuvable);
            String motif = null;
            if (preuve.operateur() != courant.operateur || !courant.reference.equals(preuve.reference())
                    || !courant.devise.equals(preuve.devise()) || preuve.montant() == null
                    || courant.montant.compareTo(preuve.montant()) != 0 || preuve.referenceOperateur() == null
                    || preuve.referenceOperateur().isBlank()
                    || (courant.referenceOperateur != null && !courant.referenceOperateur.equals(preuve.referenceOperateur()))) {
                motif = "PREUVE_INCOMPATIBLE";
            } else if (!courant.statut.reserve()) {
                if (!preuve.statut().reserve() && courant.statut != preuve.statut()) motif = "RESULTAT_TARDIF_CONTRADICTOIRE";
            } else if (preuve.statut() == CONFIRME) {
                BigDecimal nouveauMontant = echeance.montantPaye.add(courant.montant);
                if (nouveauMontant.compareTo(echeance.montantDu) > 0) {
                    motif = "SOLDE_INSUFFISANT";
                } else {
                    echeance.montantPaye = nouveauMontant;
                    courant.confirmeLe = Instant.now();
                    courant.statut = CONFIRME;
                    courant.referenceOperateur = preuve.referenceOperateur();
                    courant.modifieLe = courant.confirmeLe;
                }
            } else {
                courant.statut = preuve.statut();
                courant.referenceOperateur = preuve.referenceOperateur();
                courant.modifieLe = Instant.now();
            }
            if (motif != null) {
                courant.motifRapprochement = motif;
                courant.modifieLe = Instant.now();
            }
            return vue(courant);
        });
    }

    private TransactionPaiement paiementPersonnel(String reference) {
        return paiements.findByReferenceAndMilitantId(reference, militant())
                .orElseThrow(PaiementCotisationService::introuvable);
    }

    private Long militant() {
        var user = currentUser.requireUser();
        if (!user.roles().contains("MILITANT") || user.militantId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return user.militantId();
    }

    private static TransactionPaiement verifierDemande(TransactionPaiement paiement, Long echeanceId,
                                                       OperateurPaiement operateur, BigDecimal montant) {
        if (!echeanceId.equals(paiement.echeanceId) || paiement.operateur != operateur
                || montant.compareTo(paiement.montant) != 0) {
            throw conflit("Clé d’idempotence déjà utilisée pour une autre demande");
        }
        return paiement;
    }

    private static ClientPaiement.Demande demande(TransactionPaiement p) {
        return new ClientPaiement.Demande(p.reference, p.montant, p.devise);
    }

    private static Vue vue(TransactionPaiement p) {
        return new Vue(p.reference, p.echeanceId, p.montant, p.devise, p.operateur, p.statut,
                p.simule, p.motifRapprochement != null, p.creeLe, p.confirmeLe);
    }

    private static ResponseStatusException introuvable() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException conflit(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT, detail);
    }
}
