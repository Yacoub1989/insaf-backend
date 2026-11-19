package mr.insaf.paiement;

import mr.insaf.auth.CurrentUserService;
import mr.insaf.repository.DonRepository;

import mr.insaf.repository.TransactionDonRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.List;

@Service
public class PaiementDonService {

    private final CurrentUserService currentUser;
    private final DonRepository dons;
    private final TransactionDonRepository transactions;

    private final Map<OperateurPaiement, ClientPaiement> clients =
            new EnumMap<>(OperateurPaiement.class);

    public PaiementDonService(
            CurrentUserService currentUser,
            DonRepository dons,
            TransactionDonRepository transactions,
            ObjectProvider<BankilyClient> bankily,
            ObjectProvider<SedadClient> sedad,
            ObjectProvider<MasrviClient> masrvi
    ) {
        this.currentUser = currentUser;
        this.dons = dons;
        this.transactions = transactions;

        bankily.ifAvailable(c ->
                clients.put(OperateurPaiement.BANKILY, c));

        sedad.ifAvailable(c ->
                clients.put(OperateurPaiement.SEDAD, c));

        masrvi.ifAvailable(c ->
                clients.put(OperateurPaiement.MASRVI, c));
    }

    public record Vue(
            String reference,
            Long donId,
            BigDecimal montant,
            String devise,
            OperateurPaiement operateur,
            StatutPaiement statut,
            boolean simulation,
            Instant creeLe,
            Instant confirmeLe
    ) {}

    @Transactional
    public Vue payer(
            Long donId,
            OperateurPaiement operateur,
            UUID idempotence
    ) {
        Long militantId = militant();

        if (donId == null || donId <= 0
                || operateur == null || idempotence == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Demande de paiement invalide"
            );
        }

        // Verrouiller le don et vérifier son propriétaire.
        var don = dons.verrouiller(donId, militantId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Don introuvable"
                        ));

        String cle = idempotence.toString();

        // Une clé déjà utilisée doit correspondre
        // exactement à la même demande.
        var existante = transactions
                .findByMilitantIdAndCleIdempotence(militantId, cle);

        if (existante.isPresent()) {
            var t = existante.get();

            if (!donId.equals(t.donId)
                    || t.operateur != operateur) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Clé d'idempotence déjà utilisée"
                );
            }

            return vue(t);
        }

        if (!"EN_ATTENTE".equals(don.statut)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ce don n'est plus en attente"
            );
        }

        if (don.montant == null
                || don.montant.signum() <= 0
                || don.montant.scale() > 2) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Montant du don invalide"
            );
        }

        // Pour l'instant, seuls les simulateurs
        // sont autorisés.
        ClientPaiement client = clients.get(operateur);

        if (!(client instanceof SimulationPaiement.Simulateur)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Opérateur indisponible"
            );
        }

        TransactionDon paiement = new TransactionDon();

        paiement.reference = UUID.randomUUID().toString();
        paiement.donId = donId;
        paiement.militantId = militantId;
        paiement.montant = don.montant;
        paiement.devise = "MRU";
        paiement.operateur = operateur;
        paiement.statut = StatutPaiement.INITIE;
        paiement.cleIdempotence = cle;
        paiement.simule = true;
        paiement.creeLe = Instant.now();
        paiement.modifieLe = paiement.creeLe;

        transactions.saveAndFlush(paiement);

        // Simulation : confirmation automatique.
        var resultat = client.initier(
                new ClientPaiement.Demande(
                        paiement.reference,
                        paiement.montant,
                        paiement.devise
                )
        );

        // Vérifier la cohérence du résultat.
        if (resultat == null
                || resultat.operateur() != operateur
                || !paiement.reference.equals(resultat.reference())
                || !paiement.devise.equals(resultat.devise())
                || resultat.montant() == null
                || paiement.montant.compareTo(resultat.montant()) != 0
                || resultat.referenceOperateur() == null
                || resultat.referenceOperateur().isBlank()
                || resultat.statut() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Résultat de paiement incompatible"
            );
        }

        paiement.referenceOperateur =
                resultat.referenceOperateur();

        paiement.statut = resultat.statut();
        paiement.modifieLe = Instant.now();

        if (paiement.statut == StatutPaiement.CONFIRME) {

            paiement.confirmeLe = paiement.modifieLe;

            don.statut = "CONFIRME";
            don.referencePaiement = paiement.reference;

        } else if (paiement.statut == StatutPaiement.ECHOUE
                || paiement.statut == StatutPaiement.ANNULE) {

            don.statut = "EN_ATTENTE";
        }

        transactions.save(paiement);
        dons.save(don);

        return vue(paiement);
    }

    public Vue consulter(String reference) {
        Long militantId = militant();

        return transactions
                .findByReferenceAndMilitantId(
                        reference,
                        militantId
                )
                .map(PaiementDonService::vue)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND
                        ));
    }

    private Long militant() {
        var user = currentUser.requireUser();

        if (!user.roles().contains("MILITANT")
                || user.militantId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN
            );
        }

        return user.militantId();
    }

    private static Vue vue(TransactionDon p) {
        return new Vue(
                p.reference,
                p.donId,
                p.montant,
                p.devise,
                p.operateur,
                p.statut,
                p.simule,
                p.creeLe,
                p.confirmeLe
        );
    }

    public record Historique(
            List<Vue> paiements,
            int page,
            boolean pageSuivante
    ) {}


    public Historique historique(int numero, int taille) {

        Long militantId = militant();

        if (numero < 0 || taille < 1 || taille > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Pagination invalide"
            );
        }

        var pagination = PageRequest.of(
                numero,
                taille,
                Sort.by(
                        Sort.Direction.DESC,
                        "creeLe",
                        "id"
                )
        );

        var resultat = transactions
                .findByMilitantIdOrderByCreeLeDesc(
                        militantId,
                        pagination
                );

        return new Historique(
                resultat.getContent()
                        .stream()
                        .map(PaiementDonService::vue)
                        .toList(),
                numero,
                resultat.hasNext()
        );
    }

}
