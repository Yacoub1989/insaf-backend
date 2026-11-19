package mr.insaf.repository;

import mr.insaf.model.Don;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import mr.insaf.paiement.TransactionDon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import java.util.Optional;

public interface TransactionDonRepository
        extends JpaRepository<TransactionDon, Long> {

    // Rechercher une transaction par sa référence
    Optional<TransactionDon> findByReference(String reference);

    // Rechercher une transaction appartenant à un militant
    Optional<TransactionDon> findByReferenceAndMilitantId(
            String reference,
            Long militantId
    );

    // Empêcher les doubles paiements lors des réessais
    Optional<TransactionDon> findByMilitantIdAndCleIdempotence(
            Long militantId,
            String cleIdempotence
    );

    // Historique des paiements d'un militant
    Page<TransactionDon> findByMilitantIdOrderByCreeLeDesc(
            Long militantId,
            Pageable pageable
    );

    // Historique des transactions d'un don
    Page<TransactionDon> findByDonIdOrderByCreeLeDesc(
            Long donId,
            Pageable pageable
    );


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


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT t
    FROM TransactionDon t
    WHERE t.reference = :reference
      AND t.militantId = :militantId
    """)
    Optional<TransactionDon> verrouiller(
            @Param("reference") String reference,
            @Param("militantId") Long militantId
    );


}

