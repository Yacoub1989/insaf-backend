package mr.insaf.config;

import mr.insaf.auth.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@Profile("dev & !prod & !production")
@ConditionalOnProperty(name = "app.dev-auth.enabled", havingValue = "true")
public class DevAuthenticationConfig {
    @Bean
    DaoAuthenticationProvider devAuthenticationProvider(
            @Value("${app.dev-auth.admin-password}") String adminPassword,
            @Value("${app.dev-auth.alice-password}") String alicePassword,
            @Value("${app.dev-auth.bob-password}") String bobPassword,
            @Value("${app.dev-auth.alice-militant-id}") long aliceId,
            @Value("${app.dev-auth.bob-militant-id}") long bobId) {
        if (aliceId <= 0 || bobId <= 0 || aliceId == bobId
                || adminPassword.equals(alicePassword) || adminPassword.equals(bobPassword)
                || alicePassword.equals(bobPassword)
                || List.of(adminPassword, alicePassword, bobPassword).stream().anyMatch(p -> p.length() < 12)) {
            throw new IllegalStateException("Configurer trois mots de passe distincts et deux identifiants fictifs distincts");
        }

        var encoder = new BCryptPasswordEncoder();
        var identities = Map.of(
                "dev-admin", new AuthenticatedUser("dev:admin", "dev-admin", Set.of("ADMIN"), null),
                "dev-alice", new AuthenticatedUser("dev:alice", "dev-alice", Set.of("MILITANT"), aliceId),
                "dev-bob", new AuthenticatedUser("dev:bob", "dev-bob", Set.of("MILITANT"), bobId));
        var users = new InMemoryUserDetailsManager(
                account(identities.get("dev-admin"), adminPassword, encoder),
                account(identities.get("dev-alice"), alicePassword, encoder),
                account(identities.get("dev-bob"), bobPassword, encoder));
        var provider = new DaoAuthenticationProvider(users) {
            @Override
            protected Authentication createSuccessAuthentication(Object principal, Authentication authentication,
                                                                 UserDetails user) {
                return super.createSuccessAuthentication(identities.get(user.getUsername()), authentication, user);
            }
        };
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    private UserDetails account(AuthenticatedUser user, String password, BCryptPasswordEncoder encoder) {
        return User.withUsername(user.username()).password(encoder.encode(password))
                .roles(user.roles().toArray(String[]::new)).build();
    }
}
