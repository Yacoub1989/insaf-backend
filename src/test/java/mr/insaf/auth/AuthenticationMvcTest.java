package mr.insaf.auth;

import mr.insaf.api.ApiController;
import mr.insaf.api.SessionController;
import mr.insaf.config.DevAuthenticationConfig;
import mr.insaf.config.SecurityConfig;
import mr.insaf.model.CategorieCotisation;
import mr.insaf.model.CotisationEcheance;
import mr.insaf.model.MilitantReference;
import mr.insaf.repository.CategorieCotisationRepository;
import mr.insaf.repository.CotisationEcheanceRepository;
import mr.insaf.repository.DonRepository;
import mr.insaf.repository.MilitantReferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import com.jayway.jsonpath.JsonPath;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.SQLException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class AuthenticationMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean MilitantReferenceRepository militants;
    @MockitoBean CategorieCotisationRepository categories;
    @MockitoBean CotisationEcheanceRepository echeances;
    @MockitoBean DonRepository dons;

    String credentials(String user) {
        return "Basic " + HttpHeaders.encodeBasicAuth("dev-" + user, "fictitious-" + user, null);
    }
}

@WebMvcTest(controllers = {ApiController.class, SessionController.class},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class, properties = {
        "INSAF_DEV_AUTH_ENABLED=true",
        "INSAF_DEV_ADMIN_PASSWORD=fictitious-admin",
        "INSAF_DEV_ALICE_PASSWORD=fictitious-alice",
        "INSAF_DEV_BOB_PASSWORD=fictitious-bob",
        "INSAF_DEV_ALICE_MILITANT_ID=101",
        "INSAF_DEV_BOB_MILITANT_ID=202"
})
@Import({SecurityConfig.class, DevAuthenticationConfig.class, SecurityContextCurrentUserService.class})
@ActiveProfiles("dev")
class DevAuthenticationMvcTest extends AuthenticationMvcTest {
    @Test
    void meReturnsServerIdentityAndRoles() throws Exception {
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, credentials("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("dev:alice"))
                .andExpect(jsonPath("$.username").value("dev-alice"))
                .andExpect(jsonPath("$.roles[0]").value("MILITANT"))
                .andExpect(jsonPath("$.militantId").doesNotExist());
    }

    @Test
    void anonymousFetchDoesNotPromptButExplicitNavigationDoes() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
        mvc.perform(get("/api/me").header("Sec-Fetch-Mode", "navigate"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"INSAF Development\""));
    }

    @Test
    void militantsOnlySeeTheirOwnEcheancesEvenWhenSupplyingAnotherIdentity() throws Exception {
        when(echeances.findAllByMilitant_Id(101L)).thenReturn(List.of(echeance(1L, 101L)));
        when(echeances.findAllByMilitant_Id(202L)).thenReturn(List.of(echeance(2L, 202L)));

        mvc.perform(get("/api/echeances").header(HttpHeaders.AUTHORIZATION, credentials("alice"))
                        .param("militantId", "202").header("X-Militant-Id", "202"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].militantId").value(101));
        mvc.perform(get("/api/echeances").header(HttpHeaders.AUTHORIZATION, credentials("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].militantId").value(202));

        verify(echeances).findAllByMilitant_Id(101L);
        verify(echeances).findAllByMilitant_Id(202L);
        verify(echeances, never()).findAll();
    }

    @Test
    void adminKeepsAccessAndMilitantCannotReadOtherFinancialResources() throws Exception {
        mvc.perform(get("/api/echeances").header(HttpHeaders.AUTHORIZATION, credentials("admin")))
                .andExpect(status().isOk());
        verify(echeances).findAll();
        for (String route : List.of("/api/militants", "/api/categories", "/api/dons")) {
            mvc.perform(get(route).header(HttpHeaders.AUTHORIZATION, credentials("alice")))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(militants, categories, dons);
    }

    @Test
    void csrfAndRoleAreBothRequiredForWrites() throws Exception {
        var result = mvc.perform(get("/api/csrf").header(HttpHeaders.AUTHORIZATION, credentials("alice")))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");

        mvc.perform(post("/api/echeances").session(session).header(HttpHeaders.AUTHORIZATION, credentials("alice"))
                        .header("X-CSRF-TOKEN", token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/echeances").header(HttpHeaders.AUTHORIZATION, credentials("admin")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(echeances);

        var admin = mvc.perform(get("/api/csrf").header(HttpHeaders.AUTHORIZATION, credentials("admin")))
                .andExpect(status().isOk()).andReturn();
        String adminToken = JsonPath.read(admin.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/echeances").session((MockHttpSession) admin.getRequest().getSession(false))
                        .header(HttpHeaders.AUTHORIZATION, credentials("admin")).header("X-CSRF-TOKEN", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"militantId":202,"categorieId":10,"dateDebut":"2026-11-01",
                                 "dateFin":"2026-11-30","dateEcheance":"2026-11-30"}
                                """))
                .andExpect(status().isNotFound());
        verify(militants).findById(202L);
    }

    private static final String ECHEANCE = """
            {"militantId":101,"categorieId":10,"dateDebut":"2026-11-01",
             "dateFin":"2026-11-30","dateEcheance":"2026-11-30",
             "montantPaye":999,"montantDu":999,"statut":"PAYE"}
            """;

    @Test
    void adminCreatesCategoryAndEcheanceWithServerFinancialValues() throws Exception {
        when(categories.save(any())).thenAnswer(call -> {
            CategorieCotisation categorie = call.getArgument(0);
            categorie.id = 10L;
            return categorie;
        });
        postAsAdmin("/api/categories", """
                {"code":"MENS","libelle":"Mensuelle","montant":"10.15","periodicite":"MENSUELLE"}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.montant").value(10.15));

        var exemple = echeance(1L, 101L);
        when(militants.findById(101L)).thenReturn(Optional.of(exemple.militant));
        when(categories.findById(10L)).thenReturn(Optional.of(exemple.categorie));
        when(echeances.save(any())).thenAnswer(call -> {
            CotisationEcheance saved = call.getArgument(0);
            saved.id = 1L;
            return saved;
        });
        postAsAdmin("/api/echeances", ECHEANCE).andExpect(status().isCreated())
                .andExpect(jsonPath("$.montantDu").value(10.15))
                .andExpect(jsonPath("$.montantPaye").value(0))
                .andExpect(jsonPath("$.solde").value(10.15))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.categorieLibelle").value("Mensuelle"))
                .andExpect(jsonPath("$.dateDebut").value("2026-11-01"));
    }

    @Test
    void balanceUsesExactDecimalAndPreservesStoredStatus() throws Exception {
        var exemple = echeance(1L, 101L);
        exemple.montantPaye = new BigDecimal("0.20");
        when(echeances.findAllByMilitant_Id(101L)).thenReturn(List.of(exemple));
        mvc.perform(get("/api/echeances").header(HttpHeaders.AUTHORIZATION, credentials("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].solde").value(9.95))
                .andExpect(jsonPath("$[0].statut").value("EN_ATTENTE"));
    }

    @Test
    void invalidPeriodAndExcessPrecisionAreRejected() throws Exception {
        postAsAdmin("/api/echeances", ECHEANCE.replace("2026-11-01", "2026-12-01"))
                .andExpect(status().isBadRequest());
        postAsAdmin("/api/categories", """
                {"code":"MENS","libelle":"Mensuelle","montant":"1.015","periodicite":"MENSUELLE"}
                """).andExpect(status().isBadRequest());
        verifyNoInteractions(militants, categories, echeances);
    }

    @Test
    void mysqlUniqueConstraintViolationsReturnConflict() throws Exception {
        var duplicate = new DataIntegrityViolationException("duplicate",
                new SQLException("duplicate key", "23000", 1062));
        when(categories.save(any())).thenThrow(duplicate);
        postAsAdmin("/api/categories", """
                {"code":"MENS","libelle":"Mensuelle","montant":"10.15","periodicite":"MENSUELLE"}
                """).andExpect(status().isConflict());

        var exemple = echeance(1L, 101L);
        when(militants.findById(101L)).thenReturn(Optional.of(exemple.militant));
        when(categories.findById(10L)).thenReturn(Optional.of(exemple.categorie));
        when(echeances.save(any())).thenThrow(duplicate);
        postAsAdmin("/api/echeances", ECHEANCE).andExpect(status().isConflict());
    }

    private ResultActions postAsAdmin(String route, String json) throws Exception {
        var result = mvc.perform(get("/api/csrf").header(HttpHeaders.AUTHORIZATION, credentials("admin")))
                .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        return mvc.perform(post(route).session((MockHttpSession) result.getRequest().getSession(false))
                .header(HttpHeaders.AUTHORIZATION, credentials("admin")).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private CotisationEcheance echeance(long id, long militantId) {
        var echeance = new CotisationEcheance();
        echeance.id = id;
        echeance.militant = new MilitantReference();
        echeance.militant.id = militantId;
        echeance.categorie = new CategorieCotisation();
        echeance.categorie.id = 10L;
        echeance.categorie.code = "MENS";
        echeance.categorie.libelle = "Mensuelle";
        echeance.categorie.montant = new BigDecimal("10.15");
        echeance.montantDu = echeance.categorie.montant;
        echeance.dateEcheance = LocalDate.of(2026, 11, 30);
        return echeance;
    }
}

@WebMvcTest(controllers = {ApiController.class, SessionController.class},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class,
        properties = "INSAF_DEV_AUTH_ENABLED=true")
@Import({SecurityConfig.class, DevAuthenticationConfig.class, SecurityContextCurrentUserService.class})
@ActiveProfiles({"dev", "production"})
class ProductionAuthenticationMvcTest extends AuthenticationMvcTest {
    @Test
    void productionRejectsDevCredentialsEvenWithDevProfileAndFlag() throws Exception {
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, credentials("alice"))
                        .header("Sec-Fetch-Mode", "navigate"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
        mvc.perform(get("/api/echeances").header(HttpHeaders.AUTHORIZATION, credentials("admin")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(echeances);
    }
}

@WebMvcTest(controllers = {ApiController.class, SessionController.class},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class,
        properties = "INSAF_DEV_AUTH_ENABLED=false")
@Import({SecurityConfig.class, DevAuthenticationConfig.class, SecurityContextCurrentUserService.class})
@ActiveProfiles("dev")
class DisabledDevAuthenticationMvcTest extends AuthenticationMvcTest {
    @Test
    void devProfileAloneDoesNotEnableFictitiousAccounts() throws Exception {
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, credentials("alice"))
                        .header("Sec-Fetch-Mode", "navigate"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }
}
