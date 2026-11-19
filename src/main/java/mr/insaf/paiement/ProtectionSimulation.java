
package mr.insaf.paiement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

public class ProtectionSimulation
        implements EnvironmentPostProcessor, Ordered {

    static final String PROFIL =
            "(dev | test) & !prod & !production";

    static final String DRAPEAU =
            "app.paiements.simulation";

    private static final String BASE_LOCALE =
            "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/insaf_payments_test";

    private static final String BASE_RDS_TEST =
            "jdbc:mysql://cmc-database\\.cbo2fkkaanyx\\.eu-north-1\\.rds\\.amazonaws\\.com:3306/insaf_database(?:\\?.*)?";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment env,
            SpringApplication application) {

        boolean simulation = env.getProperty(
                DRAPEAU, Boolean.class, false);

        if (!simulation) {
            return;
        }

        if (!env.acceptsProfiles(Profiles.of(PROFIL))) {
            throw new IllegalStateException(
                    "Simulation interdite hors profil dev/test");
        }

        String url = env.getProperty(
                "spring.datasource.url", "");

        boolean baseLocale = url.matches(BASE_LOCALE);

        boolean baseRdsTest = url.matches(BASE_RDS_TEST)
                && env.getProperty(
                "app.paiements.autoriser-rds-test",
                Boolean.class,
                false);

        if ((!baseLocale && !baseRdsTest)
                || env.containsProperty("spring.flyway.url")
                || env.containsProperty(
                "spring.datasource.hikari.jdbc-url")
                || env.containsProperty(
                "spring.datasource.hikari.data-source-class-name")
                || env.containsProperty(
                "spring.datasource.jndi-name")) {

            throw new IllegalStateException(
                    "Simulation interdite : base non autorisée");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
