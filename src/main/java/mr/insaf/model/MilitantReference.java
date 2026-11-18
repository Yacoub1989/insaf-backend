package mr.insaf.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "militant_reference")
public class MilitantReference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "crm_militant_id", unique = true)
    public String crmMilitantId;
    public String nom;
    public String prenom;
    public String telephone;
    public String source = "LOCAL";
}
