
package mr.insaf.api;

import mr.insaf.auth.CurrentUserService;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final CurrentUserService currentUser;

    public SessionController(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    public record Profile(
            String subject,
            String username,
            Set<String> roles,
            Long militantId
    ) {}

    @GetMapping("/me")
    public Profile me() {
        var user = currentUser.requireUser();

        return new Profile(
                user.subject(),
                user.username(),
                user.roles(),
                user.militantId()
        );
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        currentUser.requireUser();
        return token;
    }
}
