package mr.insaf.paiement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/paiements")
public class PaiementController {
    private final PaiementCotisationService service;

    public PaiementController(PaiementCotisationService service) {
        this.service = service;
    }

    public record Demande(@NotNull @Positive Long echeanceId, @NotNull OperateurPaiement operateur,
                         @NotNull @Pattern(regexp = "[0-9]{1,13}(\\.[0-9]{1,2})?") String montant) {}

    @GetMapping("/operateurs")
    public List<PaiementCotisationService.Disponibilite> operateurs() {
        return service.operateurs();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PaiementCotisationService.Vue initier(@Valid @RequestBody Demande demande,
                                               @RequestHeader("Idempotency-Key") UUID cle) {
        return service.initier(demande.echeanceId(), demande.operateur(), new BigDecimal(demande.montant()), cle);
    }

    @GetMapping("/{reference}")
    public PaiementCotisationService.Vue consulter(@PathVariable UUID reference) {
        return service.consulter(reference.toString());
    }

    @GetMapping
    public PaiementCotisationService.Historique historique(@RequestParam(required = false) @Positive Long echeanceId,
                                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int taille) {
        return service.historique(echeanceId, page, taille);
    }
}
