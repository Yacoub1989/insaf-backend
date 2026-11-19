package mr.insaf.repository;

import jakarta.persistence.LockModeType;
import mr.insaf.model.CotisationEcheance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CotisationEcheanceRepository extends JpaRepository<CotisationEcheance, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CotisationEcheance e where e.id = :id and e.militant.id = :militantId")
    Optional<CotisationEcheance> verrouiller(Long id, Long militantId);

    @Override
    @EntityGraph(attributePaths = {"militant", "categorie"})
    List<CotisationEcheance> findAll();

    @EntityGraph(attributePaths = {"militant", "categorie"})
    List<CotisationEcheance> findAllByMilitant_Id(Long militantId);
}
