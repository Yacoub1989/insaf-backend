
package mr.insaf.paiement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import java.util.UUID;

@RestController
@RequestMapping("/api/mes-dons")
public class DonMilitantController {

    private final PaiementDonService service;
    private final DonMilitantService donService;

    public DonMilitantController(
            PaiementDonService service,
            DonMilitantService donService
    ) {
        this.service = service;
        this.donService = donService;
    }

    public record DemandePaiement(
            @NotNull
            @Positive
            Long donId,

            @NotNull
            OperateurPaiement operateur
    ) {}

    @PostMapping("/paiements")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PaiementDonService.Vue payer(
            @Valid @RequestBody DemandePaiement demande,
            @RequestHeader("Idempotency-Key") UUID cle
    ) {
        return service.payer(
                demande.donId(),
                demande.operateur(),
                cle
        );
    }

    @GetMapping("/paiements/{reference}")
    public PaiementDonService.Vue consulter(
            @PathVariable String reference
    ) {
        return service.consulter(reference);
    }


    public record DemandeDon(
            @NotNull
            @DecimalMin("0.01")
            @Digits(integer = 13, fraction = 2)
            BigDecimal montant,

            @Size(max = 1000)
            String description
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DonMilitantService.Vue creer(
            @Valid @RequestBody DemandeDon demande
    ) {
        return donService.creer(
                demande.montant(),
                demande.description()
        );
    }

    @GetMapping
    public List<DonMilitantService.Vue> mesDons() {
        return donService.mesDons();
    }


    @GetMapping("/paiements")
    public PaiementDonService.Historique historique(
            @RequestParam(defaultValue = "0")
            @Min(0) int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100) int taille
    ) {
        return service.historique(page, taille);
    }


}
