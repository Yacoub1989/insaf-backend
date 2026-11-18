package mr.insaf.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cotisation_echeance")
public class CotisationEcheance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "militant_id", nullable = false)
    public MilitantReference militant;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id", nullable = false)
    public CategorieCotisation categorie;
    @Column(name = "date_debut")
    public LocalDate dateDebut;
    @Column(name = "date_fin")
    public LocalDate dateFin;
    @Column(name = "date_echeance")
    public LocalDate dateEcheance;
    @Column(name = "montant_du")
    public BigDecimal montantDu;
    @Column(name = "montant_paye")
    public BigDecimal montantPaye = BigDecimal.ZERO;
    public String statut = "EN_ATTENTE";
}
