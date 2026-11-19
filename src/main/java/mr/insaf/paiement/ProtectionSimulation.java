package mr.insaf.paiement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

public class ProtectionSimulation implements EnvironmentPostProcessor, Ordered {
    static final String PROFIL = "(dev | test) & !prod & !production";
    static final String DRAPEAU = "app.paiements.simulation";
    private static final String BASE_ISOLEE =
            "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/insaf_payments_test";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (!env.getProperty(DRAPEAU, Boolean.class, false) || !env.acceptsProfiles(Profiles.of(PROFIL))) {
            return;
        }
        // Check before Flyway. Disallow alternate pools or JDBC options that could change the target.
        String url = env.getProperty("spring.datasource.url", "");
        if (!url.matches(BASE_ISOLEE) || env.containsProperty("spring.flyway.url")
                || env.containsProperty("spring.datasource.hikari.jdbc-url")
                || env.containsProperty("spring.datasource.hikari.data-source-class-name")
                || env.containsProperty("spring.datasource.jndi-name")) {
            throw new IllegalStateException("La simulation exige une base MySQL locale dédiée : insaf_payments_test");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
