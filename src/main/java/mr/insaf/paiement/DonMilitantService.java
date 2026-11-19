package mr.insaf.paiement;


import mr.insaf.auth.CurrentUserService;
import mr.insaf.model.Don;
import mr.insaf.model.MilitantReference;
import mr.insaf.repository.DonRepository;

import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DonMilitantService {

    private final CurrentUserService currentUser;
    private final DonRepository dons;
    private final EntityManager entityManager;

    public DonMilitantService(
            CurrentUserService currentUser,
            DonRepository dons,
            EntityManager entityManager
    ) {
        this.currentUser = currentUser;
        this.dons = dons;
        this.entityManager = entityManager;
    }

    public record Vue(
            Long id,
            String nomDonateur,
            BigDecimal montant,
            String statut,
            String description,
            String referencePaiement
    ) {}

    @Transactional
    public Vue creer(BigDecimal montant, String description) {

        Long militantId = militant();

        if (montant == null
                || montant.signum() <= 0
                || montant.scale() > 2
                || montant.precision() - montant.scale() > 13) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Montant MRU invalide"
            );
        }

        if (description != null && description.length() > 1000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Description trop longue"
            );
        }

        MilitantReference militant = entityManager.find(
                MilitantReference.class,
                militantId
        );

        if (militant == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Militant introuvable"
            );
        }

        Don don = new Don();

        don.militant = militant;
        don.nomDonateur = (
                (militant.prenom == null ? "" : militant.prenom)
                        + " "
                        + (militant.nom == null ? "" : militant.nom)
        ).trim();

        if (don.nomDonateur.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nom du militant non renseigné"
            );
        }

        don.montant = montant;
        don.description = description;
        don.statut = "EN_ATTENTE";

        return vue(dons.saveAndFlush(don));
    }

    @Transactional(readOnly = true)
    public List<Vue> mesDons() {
        return dons.findByMilitant_IdOrderByIdDesc(
                militant()
        ).stream().map(DonMilitantService::vue).toList();
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

    private static Vue vue(Don don) {
        return new Vue(
                don.id,
                don.nomDonateur,
                don.montant,
                don.statut,
                don.description,
                don.referencePaiement
        );
    }
}
