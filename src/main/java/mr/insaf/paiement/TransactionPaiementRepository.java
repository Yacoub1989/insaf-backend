package mr.insaf.paiement;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

public interface TransactionPaiementRepository extends JpaRepository<TransactionPaiement, Long> {
    Optional<TransactionPaiement> findByReferenceAndMilitantId(String reference, Long militantId);

    Optional<TransactionPaiement> findByMilitantIdAndCleIdempotence(Long militantId, String cle);

    Slice<TransactionPaiement> findByMilitantId(Long militantId, Pageable page);

    Slice<TransactionPaiement> findByMilitantIdAndEcheanceId(Long militantId, Long echeanceId, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TransactionPaiement p where p.reference = :reference and p.militantId = :militantId")
    Optional<TransactionPaiement> verrouiller(String reference, Long militantId);

    @Query("select sum(p.montant) from TransactionPaiement p where p.echeanceId = :echeanceId and p.statut in :statuts")
    BigDecimal totalReserve(Long echeanceId, Collection<StatutPaiement> statuts);
}
