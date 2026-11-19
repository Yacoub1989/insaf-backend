package mr.insaf.auth;

import java.security.Principal;
import java.util.Set;

/** Identity resolved by a trusted server-side authentication provider. */
public record AuthenticatedUser(String subject, String username, Set<String> roles, Long militantId)
        implements Principal {
    public AuthenticatedUser {
        roles = Set.copyOf(roles);
    }

    @Override
    public String getName() {
        return username;
    }
}
