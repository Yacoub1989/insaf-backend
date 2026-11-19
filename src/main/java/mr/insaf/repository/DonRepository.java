package mr.insaf.repository;

import mr.insaf.model.Don;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DonRepository extends JpaRepository<Don, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT d
        FROM Don d
        WHERE d.id = :donId
          AND d.militant.id = :militantId
        """)
    Optional<Don> verrouiller(
            @Param("donId") Long donId,
            @Param("militantId") Long militantId
    );

    List<Don> findByMilitant_IdOrderByIdDesc(Long militantId);

    Page<Don> findAll(Pageable pageable);


    interface DonAdminProjection {
        Long getId();
        String getNomDonateur();
        java.math.BigDecimal getMontant();
        String getStatut();
        String getDescription();
        Long getMilitantId();
        String getReferencePaiement();
    }

    @Query("""
    SELECT
        d.id AS id,
        d.nomDonateur AS nomDonateur,
        d.montant AS montant,
        d.statut AS statut,
        d.description AS description,
        d.militant.id AS militantId,
        d.referencePaiement AS referencePaiement
    FROM Don d
    """)
    Page<DonAdminProjection> rechercherDonsAdmin(Pageable pageable);

}

