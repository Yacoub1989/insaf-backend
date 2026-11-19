package mr.insaf.paiement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class SimulationPaiementTest {
    @Test
    void productionProfilesOverrideDevAndTestEvenWithFlag() {
        for (String profile : new String[]{"prod", "production"}) {
            new ApplicationContextRunner()
                    .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev", "test", profile))
                    .withPropertyValues(ProtectionSimulation.DRAPEAU + "=true")
                    .withUserConfiguration(SimulationPaiement.class, SimulationPaiementController.class)
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(SimulationPaiementController.class);
                        assertThat(context).doesNotHaveBean(BankilyClient.class);
                        assertThat(context).doesNotHaveBean(SedadClient.class);
                        assertThat(context).doesNotHaveBean(MasrviClient.class);
                    });
        }
    }

    @Test
    void enabledDevRequiresExplicitIsolatedMysqlBeforeFlyway() {
        var env = new MockEnvironment().withProperty(ProtectionSimulation.DRAPEAU, "true")
                .withProperty("spring.datasource.url", "jdbc:mysql://remote.example:3306/insaf_test");
        env.setActiveProfiles("dev");
        assertThatThrownBy(() -> new ProtectionSimulation().postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class);
        env.setProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:33462/insaf_payments_test");
        new ProtectionSimulation().postProcessEnvironment(env, null);
    }

    @Test
    void simulatorsAreDeterministicAndIndependent() {
        var config = new SimulationPaiement();
        var demande = new ClientPaiement.Demande("reference", new BigDecimal("10.15"), "MRU");
        for (var client : new ClientPaiement[]{config.bankilyClient(), config.sedadClient(), config.masrviClient()}) {
            var resultat = client.initier(demande);
            assertThat(resultat).isEqualTo(client.initier(demande));
            assertThat(client.verifier(demande, resultat.referenceOperateur())).isEqualTo(resultat);
        }
        assertThat(config.bankilyClient().initier(demande).referenceOperateur())
                .isNotEqualTo(config.sedadClient().initier(demande).referenceOperateur());
    }
}
