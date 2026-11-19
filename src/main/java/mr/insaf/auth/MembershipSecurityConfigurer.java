package mr.insaf.auth;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Future integration point for membership OIDC or a verified membership session.
 * The provider must create an AuthenticatedUser from verified identity and resolve roles
 * and the local militant association on the server.
 */
public interface MembershipSecurityConfigurer {
    void configure(HttpSecurity http) throws Exception;
}
