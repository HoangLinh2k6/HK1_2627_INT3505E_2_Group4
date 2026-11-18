package group4.backend.soa.auth;

import group4.backend.soa.entity.User;
import group4.backend.soa.enums.Role;
import group4.backend.soa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountProvisioning {
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;

    @Transactional
    public void provision(Environment env) {
        String email = AuthService.normalizeEmail(env.getRequiredProperty("AUTH_ACCOUNT_EMAIL"));
        Role role = Role.valueOf(env.getRequiredProperty("AUTH_ACCOUNT_ROLE"));
        String password = env.getRequiredProperty("AUTH_ACCOUNT_PASSWORD");
        if (email.isBlank() || email.length() > 254 || !email.contains("@")) {
            throw new IllegalArgumentException("AUTH_ACCOUNT_EMAIL must be a valid email");
        }
        AuthService.validatePassword(password);
        User user = users.lockByEmail(email).orElse(null);
        if (user == null) {
            String name = env.getRequiredProperty("AUTH_ACCOUNT_NAME").strip();
            if (name.isEmpty() || name.length() > 255) {
                throw new IllegalArgumentException("AUTH_ACCOUNT_NAME must contain 1 to 255 characters");
            }
            user = new User();
            user.setEmail(email);
            user.setFullName(name);
            user.setRole(role);
        } else {
            if (user.getRole() != role) {
                throw new IllegalArgumentException("Existing account has a different role");
            }
            sessions.revokeForUser(user.getId());
        }
        user.setPasswordHash(passwords.encode(password));
        users.save(user);
    }
}
