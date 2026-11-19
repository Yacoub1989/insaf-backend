
package mr.insaf.config;

import mr.insaf.auth.AuthenticatedUser;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.util.Set;

@Configuration
@Profile("dev & !prod & !production")
@ConditionalOnProperty(
        name = "app.database-auth.enabled",
        havingValue = "true"
)
public class DatabaseAuthenticationConfig {

    @Bean("devAuthenticationProvider")
    DaoAuthenticationProvider devAuthenticationProvider(
            JdbcTemplate jdbc) {

        var users = (org.springframework.security.core.userdetails.UserDetailsService)
                username -> jdbc.query(
                        """
                        SELECT username, password_hash,
                               role, militant_id
                        FROM utilisateur
                        WHERE username = ?
                          AND actif = 1
                        """,
                        rs -> {
                            if (!rs.next()) {
                                throw new UsernameNotFoundException(
                                        "Utilisateur introuvable"
                                );
                            }

                            return User.withUsername(
                                            rs.getString("username"))
                                    .password(rs.getString("password_hash"))
                                    .roles(rs.getString("role"))
                                    .build();
                        },
                        username
                );

        var provider = new DaoAuthenticationProvider(users) {

            @Override
            protected Authentication createSuccessAuthentication(
                    Object principal,
                    Authentication authentication,
                    UserDetails user) {

                AuthenticatedUser identity = jdbc.query(
                        """
                        SELECT id, username, role, militant_id
                        FROM utilisateur
                        WHERE username = ? AND actif = 1
                        """,
                        rs -> {
                            if (!rs.next()) {
                                throw new UsernameNotFoundException(
                                        "Utilisateur introuvable"
                                );
                            }

                            Long militantId =
                                    rs.getObject("militant_id", Long.class);

                            return new AuthenticatedUser(
                                    "db:" + rs.getLong("id"),
                                    rs.getString("username"),
                                    Set.of(rs.getString("role")),
                                    militantId
                            );
                        },
                        user.getUsername()
                );

                return super.createSuccessAuthentication(
                        identity, authentication, user
                );
            }
        };

        provider.setPasswordEncoder(
                PasswordEncoderFactories
                        .createDelegatingPasswordEncoder()
        );

        return provider;
    }
}
