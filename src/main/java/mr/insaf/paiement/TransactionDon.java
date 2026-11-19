
package mr.insaf.paiement;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction_don")
public class TransactionDon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 36, unique = true)
    public String reference;

    @Column(name = "militant_id", nullable = false)
    public Long militantId;

    @Column(name = "don_id", nullable = false)
    public Long donId;

    @Column(nullable = false, precision = 15, scale = 2)
    public BigDecimal montant;

    @Column(nullable = false, length = 3)
    public String devise = "MRU";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public OperateurPaiement operateur;

    @Column(name = "reference_operateur", length = 100)
    public String referenceOperateur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public StatutPaiement statut = StatutPaiement.INITIE;

    @Column(name = "cle_idempotence", nullable = false, length = 36)
    public String cleIdempotence;

    @Column(name = "simule", nullable = false)
    public boolean simule;

    @Column(name = "motif_rapprochement", length = 100)
    public String motifRapprochement;

    @Column(name = "cree_le", nullable = false)
    public Instant creeLe;

    @Column(name = "modifie_le", nullable = false)
    public Instant modifieLe;

    @Column(name = "confirme_le")
    public Instant confirmeLe;
}
