package mr.insaf.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "categorie_cotisation")
public class CategorieCotisation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(unique = true, nullable = false)
    public String code;
    public String libelle;
    public BigDecimal montant;
    public String periodicite;
    public Boolean actif = true;
}
