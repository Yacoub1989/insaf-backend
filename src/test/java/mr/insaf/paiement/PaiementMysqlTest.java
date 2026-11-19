package mr.insaf.paiement;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${INSAF_PAYMENTS_IT_URL}",
        "spring.datasource.username=${INSAF_PAYMENTS_IT_USER}",
        "spring.datasource.password=${INSAF_PAYMENTS_IT_PASSWORD}",
        "app.paiements.simulation=true",
        "INSAF_DEV_AUTH_ENABLED=true",
        "INSAF_DEV_ADMIN_PASSWORD=fictitious-admin",
        "INSAF_DEV_ALICE_PASSWORD=fictitious-alice",
        "INSAF_DEV_BOB_PASSWORD=fictitious-bob",
        "INSAF_DEV_ALICE_MILITANT_ID=101",
        "INSAF_DEV_BOB_MILITANT_ID=202"
})
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@EnabledIfEnvironmentVariable(named = "INSAF_PAYMENTS_IT_URL",
        matches = "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/insaf_payments_test")
class PaiementMysqlTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void paiementsPartielsSuccessifsEtConfirmationRepetee() throws Exception {
        long echeance = echeance(101);
        var alice = session("alice");
        String cle = UUID.randomUUID().toString();
        String ref = initier(alice, echeance, "BANKILY", "30.15", cle, 202);
        assertThat(initier(alice, echeance, "BANKILY", "30.15", cle, 202)).isEqualTo(ref);
        initier(alice, echeance, "BANKILY", "30.16", cle, 409);
        assertThat(paye(echeance)).isEqualByComparingTo("0");
        simuler(alice, ref, "SUCCES");
        simuler(alice, ref, "SUCCES");
        assertThat(paye(echeance)).isEqualByComparingTo("30.15");

        String suivant = initier(alice, echeance, "SEDAD", "69.85", UUID.randomUUID().toString(), 202);
        simuler(alice, suivant, "SUCCES");
        assertThat(paye(echeance)).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT statut FROM cotisation_echeance WHERE id = ?", String.class, echeance))
                .isEqualTo("EN_ATTENTE");
    }

    @Test
    void reservationsConcurrentesEtClesIdentiques() throws Exception {
        long echeance = echeance(101);
        var alice = session("alice");
        var autreSession = session("alice");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var depart = new CountDownLatch(1);
            Callable<Integer> reserverA = () -> {
                depart.await(5, TimeUnit.SECONDS);
                return initier(alice, echeance, "BANKILY", "70.00", UUID.randomUUID().toString());
            };
            Callable<Integer> reserverB = () -> {
                depart.await(5, TimeUnit.SECONDS);
                return initier(autreSession, echeance, "MASRVI", "70.00", UUID.randomUUID().toString());
            };
            var a = pool.submit(reserverA);
            var b = pool.submit(reserverB);
            depart.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(202, 409);

            String cle = UUID.randomUUID().toString();
            long autreEcheance = echeance(101);
            var c = pool.submit(() -> initier(alice, echeance, "BANKILY", "10.00", cle));
            var d = pool.submit(() -> initier(autreSession, autreEcheance, "BANKILY", "10.00", cle));
            assertThat(List.of(c.get(10, TimeUnit.SECONDS), d.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(202, 409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_paiement WHERE cle_idempotence = ?",
                    Integer.class, cle)).isEqualTo(1);
        }
    }

    @Test
    void echecAnnulationEtConfirmationTardive() throws Exception {
        long echeance = echeance(101);
        var alice = session("alice");
        String premier = initier(alice, echeance, "MASRVI", "100", UUID.randomUUID().toString(), 202);
        assertThat(JsonPath.<String>read(simuler(alice, premier, "ECHEC"), "$.statut")).isEqualTo("ECHOUE");
        String deuxieme = initier(alice, echeance, "SEDAD", "100", UUID.randomUUID().toString(), 202);
        assertThat(JsonPath.<String>read(simuler(alice, deuxieme, "ANNULATION"), "$.statut")).isEqualTo("ANNULE");
        String troisieme = initier(alice, echeance, "BANKILY", "100", UUID.randomUUID().toString(), 202);
        simuler(alice, troisieme, "SUCCES");
        String tardif = simuler(alice, premier, "SUCCES");
        assertThat(JsonPath.<Boolean>read(tardif, "$.rapprochement")).isTrue();
        assertThat(JsonPath.<String>read(tardif, "$.statut")).isEqualTo("ECHOUE");
        assertThat(paye(echeance)).isEqualByComparingTo("100");
    }

    @Test
    void rollbackSiLePaiementNePeutPasEtreMarqueConfirme() throws Exception {
        long echeance = echeance(101);
        var alice = session("alice");
        String ref = initier(alice, echeance, "BANKILY", "30.15", UUID.randomUUID().toString(), 202);
        jdbc.execute("""
                ALTER TABLE transaction_paiement ADD CONSTRAINT test_paiement_rollback
                CHECK (reference <> '%s' OR statut <> 'CONFIRME')
                """.formatted(ref));
        try {
            assertThatThrownBy(() -> simuler(alice, ref, "SUCCES"))
                    .hasStackTraceContaining("test_paiement_rollback");
            assertThat(paye(echeance)).isEqualByComparingTo("0");
            assertThat(jdbc.queryForObject("SELECT statut FROM transaction_paiement WHERE reference = ?", String.class, ref))
                    .isEqualTo("EN_ATTENTE");
        } finally {
            jdbc.execute("ALTER TABLE transaction_paiement DROP CHECK test_paiement_rollback");
        }
        simuler(alice, ref, "SUCCES");
        assertThat(paye(echeance)).isEqualByComparingTo("30.15");
    }

    @Test
    void proprieteHistoriqueEtCsrf() throws Exception {
        long echeance = echeance(101);
        var alice = session("alice");
        var bob = session("bob");
        String ref = initier(alice, echeance, "BANKILY", "10.10", UUID.randomUUID().toString(), 202);
        initier(bob, echeance, "BANKILY", "10.10", UUID.randomUUID().toString(), 404);
        mvc.perform(get("/api/paiements/" + ref).header(HttpHeaders.AUTHORIZATION, bob.auth()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/paiements").param("echeanceId", Long.toString(echeance))
                        .header(HttpHeaders.AUTHORIZATION, bob.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paiements").isEmpty());
        mvc.perform(get("/api/paiements").param("echeanceId", Long.toString(echeance))
                        .header(HttpHeaders.AUTHORIZATION, alice.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paiements[0].reference").value(ref));
        mvc.perform(post("/api/paiements/" + ref + "/simulation").param("scenario", "SUCCES")
                        .header(HttpHeaders.AUTHORIZATION, alice.auth()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/paiements/" + ref + "/simulation").param("scenario", "SUCCES")
                        .header(HttpHeaders.AUTHORIZATION, bob.auth()).session(bob.session()).header("X-CSRF-TOKEN", bob.csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/paiements").header(HttpHeaders.AUTHORIZATION, session("admin").auth()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/paiements")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/paiements/operateurs").header(HttpHeaders.AUTHORIZATION, alice.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[2].disponible").value(true));
    }

    private long echeance(long militantId) {
        jdbc.update("INSERT INTO militant_reference (id, nom, prenom) VALUES (?, 'Test', 'Fictif') ON DUPLICATE KEY UPDATE id = id", militantId);
        String code = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO categorie_cotisation (code, libelle, montant, periodicite) VALUES (?, 'Test', 100, 'MENSUELLE')", code);
        Long categorie = jdbc.queryForObject("SELECT id FROM categorie_cotisation WHERE code = ?", Long.class, code);
        jdbc.update("""
                INSERT INTO cotisation_echeance
                (militant_id, categorie_id, date_debut, date_fin, date_echeance, montant_du)
                VALUES (?, ?, '2026-01-01', '2026-01-31', '2026-01-31', 100)
                """, militantId, categorie);
        return jdbc.queryForObject("SELECT id FROM cotisation_echeance WHERE categorie_id = ?", Long.class, categorie);
    }

    private BigDecimal paye(long echeance) {
        return jdbc.queryForObject("SELECT montant_paye FROM cotisation_echeance WHERE id = ?", BigDecimal.class, echeance);
    }

    private record Session(String auth, MockHttpSession session, String csrf) {}

    private Session session(String user) throws Exception {
        String auth = "Basic " + HttpHeaders.encodeBasicAuth("dev-" + user, "fictitious-" + user, null);
        var result = mvc.perform(get("/api/csrf").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andReturn();
        return new Session(auth, (MockHttpSession) result.getRequest().getSession(false),
                JsonPath.read(result.getResponse().getContentAsString(), "$.token"));
    }

    private org.springframework.mock.web.MockHttpServletResponse demande(Session s, long echeance, String operateur,
                                                                          String montant, String cle) throws Exception {
        return mvc.perform(post("/api/paiements").session(s.session()).header(HttpHeaders.AUTHORIZATION, s.auth())
                        .header("X-CSRF-TOKEN", s.csrf()).header("Idempotency-Key", cle)
                        .contentType(MediaType.APPLICATION_JSON).content(
                                """
                                {"echeanceId":%d,"operateur":"%s","montant":"%s"}
                                """.formatted(echeance, operateur, montant)))
                .andReturn().getResponse();
    }

    private int initier(Session s, long echeance, String operateur, String montant, String cle) throws Exception {
        return demande(s, echeance, operateur, montant, cle).getStatus();
    }

    private String initier(Session s, long echeance, String operateur, String montant, String cle, int code) throws Exception {
        var response = demande(s, echeance, operateur, montant, cle);
        assertThat(response.getStatus()).isEqualTo(code);
        return code == 202 ? JsonPath.read(response.getContentAsString(), "$.reference") : null;
    }

    private String simuler(Session s, String ref, String scenario) throws Exception {
        return mvc.perform(post("/api/paiements/" + ref + "/simulation").param("scenario", scenario)
                        .session(s.session()).header(HttpHeaders.AUTHORIZATION, s.auth()).header("X-CSRF-TOKEN", s.csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
