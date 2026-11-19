package mr.insaf.paiement;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction_paiement")
public class TransactionPaiement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String reference;
    Long militantId;
    Long echeanceId;
    BigDecimal montant;
    String devise;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    OperateurPaiement operateur;
    String referenceOperateur;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    StatutPaiement statut;
    String cleIdempotence;
    boolean simule;
    String motifRapprochement;
    Instant creeLe;
    Instant modifieLe;
    Instant confirmeLe;

    protected TransactionPaiement() {}
}
