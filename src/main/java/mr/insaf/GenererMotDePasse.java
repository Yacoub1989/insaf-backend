package mr.insaf;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenererMotDePasse {

    public static void main(String[] args) {

        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder();

        String password = "Fatima2026!";

        String hash = "{bcrypt}" + encoder.encode(password);

        System.out.println(hash);
    }
}
