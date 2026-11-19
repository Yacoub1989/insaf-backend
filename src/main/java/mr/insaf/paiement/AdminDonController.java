
package mr.insaf.paiement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/admin/dons")
public class AdminDonController {

    private final AdminDonService service;

    public AdminDonController(AdminDonService service) {
        this.service = service;
    }

    @GetMapping
    public AdminDonService.PageDons dons(
            @RequestParam(defaultValue = "0")
            @Min(0) int page,

            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int taille
    ) {
        return service.listerDons(page, taille);
    }

    @GetMapping("/transactions")
    public AdminDonService.PageTransactions transactions(
            @RequestParam(defaultValue = "0")
            @Min(0) int page,

            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int taille
    ) {
        return service.listerTransactions(page, taille);
    }

    @GetMapping("/statistiques")
    public AdminDonService.Statistiques statistiques() {
        return service.statistiques();
    }
}
