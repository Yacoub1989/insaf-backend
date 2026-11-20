package mr.insaf.config;

import jakarta.servlet.DispatcherType;
import mr.insaf.auth.MembershipSecurityConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain chain(HttpSecurity http,
                              @Qualifier("devAuthenticationProvider") ObjectProvider<DaoAuthenticationProvider> providers,
                              ObjectProvider<MembershipSecurityConfigurer> membership) throws Exception {
        var dev = providers.getIfAvailable();
        var integration = membership.getIfAvailable();
        if (dev != null && integration != null) {
            throw new IllegalStateException("Choisir une seule source d’authentification");
        }

        var devChallenge = new BasicAuthenticationEntryPoint();
        devChallenge.setRealmName("INSAF Development");

        http.cors(Customizer.withDefaults())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> {
                    if (dev != null && "navigate".equals(request.getHeader("Sec-Fetch-Mode"))
                            && request.getRequestURI().equals(request.getContextPath() + "/api/me")) {
                        devChallenge.commence(request, response, error);
                        return;
                    }
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                }))
                .authorizeHttpRequests(access -> {
                    // Let Spring Boot return the original HTTP status for an internal error dispatch.
                    access.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            .requestMatchers("/actuator/health").permitAll();
                    if (dev != null || integration != null) {
                        access.requestMatchers(HttpMethod.GET, "/api/me", "/api/csrf").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/echeances").hasAnyRole("ADMIN", "MILITANT")
                                .requestMatchers(HttpMethod.GET, "/api/paiements", "/api/paiements/*").hasRole("MILITANT")
                                .requestMatchers(HttpMethod.POST, "/api/paiements", "/api/paiements/*/simulation")
                                .hasRole("MILITANT")
                                .requestMatchers(HttpMethod.GET, "/api/militants", "/api/categories",
                                        "/api/dons", "/actuator/info").hasRole("ADMIN")
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/mes-dons/paiements/*"
                                ).hasRole("MILITANT")
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/mes-dons"
                                ).hasRole("MILITANT")

                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/mes-dons"
                                ).hasRole("MILITANT")
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/mes-dons/paiements"
                                ).hasRole("MILITANT")
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/admin/dons",
                                        "/api/admin/dons/**"
                                ).hasRole("ADMIN")
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/paiements/operateurs"
                                ).hasRole("MILITANT")

                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/mes-dons/paiements"
                                ).hasRole("MILITANT")
                                .requestMatchers(HttpMethod.POST, "/api/militants", "/api/categories",
                                        "/api/dons", "/api/echeances").hasRole("ADMIN");
                    }
                    access.anyRequest().denyAll();
                });
        if (dev != null) {
            http.authenticationProvider(dev).httpBasic(basic -> basic.authenticationEntryPoint(devChallenge));
        }
        if (integration != null) {
            integration.configure(http);
        }
        return http.build();
    }

    @Bean
    CorsConfigurationSource cors(@Value("${app.cors-origin}") String origin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origin));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-TOKEN", "Idempotency-Key"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
