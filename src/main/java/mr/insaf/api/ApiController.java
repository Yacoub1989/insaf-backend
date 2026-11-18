package mr.insaf.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import mr.insaf.model.*;
import mr.insaf.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final MilitantReferenceRepository militants;
    private final CategorieCotisationRepository categories;
    private final CotisationEcheanceRepository echeances;
    private final DonRepository dons;

    public ApiController(MilitantReferenceRepository militants, CategorieCotisationRepository categories, CotisationEcheanceRepository echeances, DonRepository dons) {
        this.militants = militants;
        this.categories = categories;
        this.echeances = echeances;
        this.dons = dons;
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

    public record CategorieInput(@NotBlank String code, @NotBlank String libelle,
                                 @NotNull @DecimalMin("0.0") BigDecimal montant, @NotBlank String periodicite) {
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
        c = categories.save(c);
        return new CategorieView(c.id, c.code, c.libelle, c.montant, c.periodicite);
    }

    public record EcheanceInput(@NotNull Long militantId, @NotNull Long categorieId, @NotNull LocalDate dateDebut,
                                @NotNull LocalDate dateFin, @NotNull LocalDate dateEcheance) {
    }

    public record EcheanceView(Long id, Long militantId, Long categorieId, BigDecimal montantDu, BigDecimal montantPaye,
                               String statut, LocalDate dateEcheance) {
    }

    @GetMapping("/echeances")
    public List<EcheanceView> echeances() {
        return echeances.findAll().stream().map(this::toView).toList();
    }

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
        return toView(echeances.save(e));
    }

    private EcheanceView toView(CotisationEcheance e) {
        return new EcheanceView(e.id, e.militant.id, e.categorie.id, e.montantDu, e.montantPaye, e.statut, e.dateEcheance);
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
        Don d = new Don();
        d.nomDonateur = input.nomDonateur();
        d.montant = input.montant();
        d.description = input.description();
        d = dons.save(d);
        return new DonView(d.id, d.nomDonateur, d.montant, d.statut, d.description);
    }
}
