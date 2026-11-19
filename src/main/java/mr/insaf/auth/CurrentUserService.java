package mr.insaf.auth;

public interface CurrentUserService {
    AuthenticatedUser requireUser();
}
