package mr.insaf.paiement;


import mr.insaf.auth.CurrentUserService;
import mr.insaf.model.Don;
import mr.insaf.repository.DonRepository;

import mr.insaf.repository.TransactionDonRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AdminDonService {

    private final CurrentUserService currentUser;
    private final DonRepository dons;
    private final TransactionDonRepository transactions;
    private final JdbcTemplate jdbc;

    public AdminDonService(
            CurrentUserService currentUser,
            DonRepository dons,
            TransactionDonRepository transactions,
            JdbcTemplate jdbc
    ) {
        this.currentUser = currentUser;
        this.dons = dons;
        this.transactions = transactions;
        this.jdbc = jdbc;
    }

    public record DonVue(
            Long id,
            String nomDonateur,
            BigDecimal montant,
            String statut,
            String description,
            Long militantId,
            String referencePaiement
    ) {}

    public record PageDons(
            List<DonVue> dons,
            int page,
            boolean pageSuivante
    ) {}

    public record PageTransactions(
            List<PaiementDonService.Vue> transactions,
            int page,
            boolean pageSuivante
    ) {}

    public record Statistiques(
            long nombreDonsConfirmes,
            BigDecimal montantDonsConfirmes,
            long nombreTransactionsSimulees,
            BigDecimal montantSimuleConfirme,
            long nombreTransactionsReelles,
            BigDecimal montantReelConfirme
    ) {}

    @Transactional(readOnly = true)
    public PageDons listerDons(int page, int taille) {
        administrateur();
        verifierPagination(page, taille);

        var resultat = dons.rechercherDonsAdmin(
                PageRequest.of(
                        page,
                        taille,
                        Sort.by(Sort.Direction.DESC, "id")
                )
        );

        return new PageDons(
                resultat.getContent().stream()
                        .map(AdminDonService::vueDon)
                        .toList(),
                page,
                resultat.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public PageTransactions listerTransactions(
            int page,
            int taille
    ) {
        administrateur();
        verifierPagination(page, taille);

        var resultat = transactions.findAll(
                PageRequest.of(
                        page,
                        taille,
                        Sort.by(
                                Sort.Direction.DESC,
                                "creeLe",
                                "id"
                        )
                )
        );

        return new PageTransactions(
                resultat.getContent().stream()
                        .map(AdminDonService::vueTransaction)
                        .toList(),
                page,
                resultat.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public Statistiques statistiques() {
        administrateur();

        var donsConfirmes = jdbc.queryForMap("""
            SELECT
                COUNT(*) AS nombre,
                COALESCE(SUM(montant), 0) AS montant
            FROM don
            WHERE statut = 'CONFIRME'
            """);

        var simulations = jdbc.queryForMap("""
            SELECT
                COUNT(*) AS nombre,
                COALESCE(SUM(montant), 0) AS montant
            FROM transaction_don
            WHERE statut = 'CONFIRME'
              AND simule = 1
            """);

        var reels = jdbc.queryForMap("""
            SELECT
                COUNT(*) AS nombre,
                COALESCE(SUM(montant), 0) AS montant
            FROM transaction_don
            WHERE statut = 'CONFIRME'
              AND simule = 0
            """);

        return new Statistiques(
                ((Number) donsConfirmes.get("nombre")).longValue(),
                (BigDecimal) donsConfirmes.get("montant"),

                ((Number) simulations.get("nombre")).longValue(),
                (BigDecimal) simulations.get("montant"),

                ((Number) reels.get("nombre")).longValue(),
                (BigDecimal) reels.get("montant")
        );
    }

    private void administrateur() {
        var user = currentUser.requireUser();

        if (!user.roles().contains("ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Accès réservé à l'administrateur"
            );
        }
    }

    private static void verifierPagination(
            int page,
            int taille
    ) {
        if (page < 0 || taille < 1 || taille > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Pagination invalide"
            );
        }
    }


    private static DonVue vueDon(
            DonRepository.DonAdminProjection d
    ) {
        return new DonVue(
                d.getId(),
                d.getNomDonateur(),
                d.getMontant(),
                d.getStatut(),
                d.getDescription(),
                d.getMilitantId(),
                d.getReferencePaiement()
        );
    }


    private static PaiementDonService.Vue vueTransaction(
            TransactionDon t
    ) {
        return new PaiementDonService.Vue(
                t.reference,
                t.donId,
                t.montant,
                t.devise,
                t.operateur,
                t.statut,
                t.simule,
                t.creeLe,
                t.confirmeLe
        );
    }
}
