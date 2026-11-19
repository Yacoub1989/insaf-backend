package mr.insaf.paiement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Profile(ProtectionSimulation.PROFIL)
@ConditionalOnProperty(name = ProtectionSimulation.DRAPEAU, havingValue = "true")
@RequestMapping("/api/paiements/{reference}/simulation")
class SimulationPaiementController {
    private final PaiementCotisationService service;

    SimulationPaiementController(PaiementCotisationService service) {
        this.service = service;
    }

    enum Scenario {
        SUCCES(StatutPaiement.CONFIRME), ECHEC(StatutPaiement.ECHOUE), ANNULATION(StatutPaiement.ANNULE);

        private final StatutPaiement statut;

        Scenario(StatutPaiement statut) {
            this.statut = statut;
        }
    }

    @PostMapping
    PaiementCotisationService.Vue simuler(@PathVariable UUID reference, @RequestParam Scenario scenario) {
        return service.simuler(reference.toString(), scenario.statut);
    }
}
