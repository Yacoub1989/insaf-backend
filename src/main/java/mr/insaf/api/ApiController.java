package mr.insaf.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import mr.insaf.auth.CurrentUserService;
import mr.insaf.model.*;
import mr.insaf.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.SQLException;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

import mr.insaf.auth.CurrentUserService;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final MilitantReferenceRepository militants;
    private final CategorieCotisationRepository categories;
    private final CotisationEcheanceRepository echeances;
    private final DonRepository dons;
    private final CurrentUserService currentUser;

    public ApiController(MilitantReferenceRepository militants, CategorieCotisationRepository categories, CotisationEcheanceRepository echeances, DonRepository dons, CurrentUserService currentUser) {
        this.militants = militants;
        this.categories = categories;
        this.echeances = echeances;
        this.dons = dons;
        this.currentUser = currentUser;
    }

    public record MilitantInput(@NotBlank String nom, @NotBlank String prenom, String telephone) {
    }

    public record MilitantView(Long id, String nom, String prenom, String telephone, String source) {
    }

    @GetMapping("/militants")
    public List<MilitantView> militants() {
        return militants.findAll().stream().map(m -> new MilitantView(m.id, m.nom, m.prenom, m.telephone, m.source)).toList();
    }

    @PostMapping("/militants")
    @ResponseStatus(HttpStatus.CREATED)
    public MilitantView militant(@Valid @RequestBody MilitantInput input) {
        MilitantReference m = new MilitantReference();
        m.nom = input.nom();
        m.prenom = input.prenom();
        m.telephone = input.telephone();
        m = militants.save(m);
        return new MilitantView(m.id, m.nom, m.prenom, m.telephone, m.source);
    }

    public record CategorieInput(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 150) String libelle,
                                 @NotNull @DecimalMin("0.0") @Digits(integer = 13, fraction = 2) BigDecimal montant,
                                 @NotBlank @Size(max = 20) String periodicite) {
    }

    public record CategorieView(Long id, String code, String libelle, BigDecimal montant, String periodicite) {
    }

    @GetMapping("/categories")
    public List<CategorieView> categories() {
        return categories.findAll().stream().map(c -> new CategorieView(c.id, c.code, c.libelle, c.montant, c.periodicite)).toList();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategorieView categorie(@Valid @RequestBody CategorieInput input) {
        CategorieCotisation c = new CategorieCotisation();
        c.code = input.code();
        c.libelle = input.libelle();
        c.montant = input.montant();
        c.periodicite = input.periodicite();
        try {
            c = categories.save(c);
        } catch (DataIntegrityViolationException error) {
            throw duplicateOrOriginal(error, "Ce code de catégorie existe déjà");
        }
        return new CategorieView(c.id, c.code, c.libelle, c.montant, c.periodicite);
    }

    public record EcheanceInput(@NotNull @Positive Long militantId, @NotNull @Positive Long categorieId, @NotNull LocalDate dateDebut,
                                @NotNull LocalDate dateFin, @NotNull LocalDate dateEcheance) {
    }

    public record EcheanceView(Long id, Long militantId, Long categorieId, BigDecimal montantDu, BigDecimal montantPaye,
                               String statut, LocalDate dateEcheance, BigDecimal solde,
                               String categorieCode, String categorieLibelle, LocalDate dateDebut, LocalDate dateFin) {
    }

    @GetMapping("/echeances")
    public List<EcheanceView> echeances() {
        var user = currentUser.requireUser();
        if (user.roles().contains("ADMIN")) {
            return echeances.findAll().stream().map(this::toView).toList();
        }
        if (!user.roles().contains("MILITANT") || user.militantId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return echeances.findAllByMilitant_Id(user.militantId()).stream().map(this::toView).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/echeances")
    @ResponseStatus(HttpStatus.CREATED)
    public EcheanceView echeance(@Valid @RequestBody EcheanceInput input) {
        if (input.dateFin().isBefore(input.dateDebut()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periode invalide");
        CotisationEcheance e = new CotisationEcheance();
        e.militant = militants.findById(input.militantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Militant introuvable"));
        e.categorie = categories.findById(input.categorieId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categorie introuvable"));
        e.dateDebut = input.dateDebut();
        e.dateFin = input.dateFin();
        e.dateEcheance = input.dateEcheance();
        e.montantDu = e.categorie.montant;
        try {
            return toView(echeances.save(e));
        } catch (DataIntegrityViolationException error) {
            throw duplicateOrOriginal(error, "Une échéance existe déjà pour ce militant, cette catégorie et cette période");
        }
    }

    private EcheanceView toView(CotisationEcheance e) {
        return new EcheanceView(e.id, e.militant.id, e.categorie.id, e.montantDu, e.montantPaye, e.statut,
                e.dateEcheance, e.montantDu.subtract(e.montantPaye), e.categorie.code, e.categorie.libelle,
                e.dateDebut, e.dateFin);
    }

    private RuntimeException duplicateOrOriginal(DataIntegrityViolationException error, String detail) {
        // MySQL arbitrates concurrent inserts using the existing unique constraints.
        if (error.getMostSpecificCause() instanceof SQLException sql && sql.getErrorCode() == 1062) {
            return new ResponseStatusException(HttpStatus.CONFLICT, detail, error);
        }
        return error;
    }

    public record DonInput(@NotBlank String nomDonateur, @NotNull @DecimalMin(value = "0.01") BigDecimal montant,
                           String description) {
    }

    public record DonView(Long id, String nomDonateur, BigDecimal montant, String statut, String description) {
    }

    @GetMapping("/dons")
    public List<DonView> dons() {
        return dons.findAll().stream().map(d -> new DonView(d.id, d.nomDonateur, d.montant, d.statut, d.description)).toList();
    }

    @PostMapping("/dons")
    @ResponseStatus(HttpStatus.CREATED)
    public DonView don(@Valid @RequestBody DonInput input) {

        var user = currentUser.requireUser();

        if (user.roles().contains("MILITANT")) {
            if (user.militantId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Aucun militant associé au compte"
                );
            }
        }

        Don d = new Don();
        d.nomDonateur = input.nomDonateur();
        d.montant = input.montant();
        d.description = input.description();
        if (user.roles().contains("MILITANT")) {
            MilitantReference militant = militants.findById(
                    user.militantId()
            ).orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Militant introuvable"
                    )
            );

            d.militant = militant;
        }
        d = dons.save(d);
        return new DonView(d.id, d.nomDonateur, d.montant, d.statut, d.description);
    }


    @GetMapping("/dons/mes-dons")
    public List<DonView> mesDons() {

        var user = currentUser.requireUser();

        Long militantId = user.militantId();

        if (militantId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Compte militant requis"
            );
        }

        return dons.findByMilitant_IdOrderByIdDesc(militantId)
                .stream()
                .map(d -> new DonView(
                        d.id,
                        d.nomDonateur,
                        d.montant,
                        d.statut,
                        d.description
                ))
                .toList();
    }
}
