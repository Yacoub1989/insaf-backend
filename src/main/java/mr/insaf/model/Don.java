package mr.insaf.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "don")
public class Don {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "nom_donateur")
    public String nomDonateur;
    public BigDecimal montant;
    public String statut = "EN_ATTENTE";
    public String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "militant_id")
    public MilitantReference militant;

    @Column(name = "reference_paiement", length = 36, unique = true)
    public String referencePaiement;

}

