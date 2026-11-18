package group4.backend.soa.auth;

import group4.backend.soa.entity.User;
import group4.backend.soa.enums.Role;
import group4.backend.soa.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    public static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    public static final Duration REFRESH_LIFETIME = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, AuthSessionRepository sessions, PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.clock = clock;
        this.dummyHash = passwords.encode(randomToken());
    }

    @Transactional
    public User register(String email, String password, String fullName, String phone) {
        validatePassword(password);
        User user = new User();
        user.setEmail(normalizeEmail(email));
        user.setFullName(fullName.strip());
        user.setPhone(phone);
        user.setRole(Role.CUSTOMER);
        user.setPasswordHash(passwords.encode(password));
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
    }

    @Transactional
    public Tokens login(String email, String password) {
        User user = users.lockByEmail(normalizeEmail(email)).orElse(null);
        String hash = user == null ? null : user.getPasswordHash();
        boolean usableHash = hash != null && hash.startsWith("$2");
        boolean validPassword = password.getBytes(StandardCharsets.UTF_8).length <= 72
                && passwords.matches(password, usableHash ? hash : dummyHash);
        if (!usableHash || !validPassword) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        Instant now = clock.instant();
        sessions.deleteExpiredForUser(user.getId(), now);
        AuthSession session = new AuthSession();
        session.setUser(user);
        return issue(session, now);
    }

    @Transactional(readOnly = true)
    public Optional<AuthPrincipal> authenticate(String accessToken) {
        if (!isToken(accessToken)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return sessions.findByAccessHash(hash(accessToken))
                .filter(session -> session.getAccessExpiresAt().isAfter(now))
                .map(session -> new AuthPrincipal(session.getUser().getId(), session.getUser().getRole(), session.getId()));
    }

    @Transactional
    public Tokens refresh(String refreshToken) {
        if (!isToken(refreshToken)) {
            throw invalidSession();
        }
        String refreshHash = hash(refreshToken);
        AuthSession session = sessions.findByRefreshHash(refreshHash)
                .filter(found -> refreshHash.equals(found.getRefreshHash())).orElseThrow(AuthService::invalidSession);
        Instant now = clock.instant();
        if (!session.getRefreshExpiresAt().isAfter(now)) {
            throw invalidSession();
        }
        return issue(session, now);
    }

    @Transactional
    public void logout(AuthPrincipal principal, String refreshToken) {
        if (isToken(refreshToken)) {
            Optional<AuthSession> session = sessions.findByRefreshHash(hash(refreshToken));
            if (session.isPresent() && (principal == null || session.get().getId().equals(principal.sessionId()))) {
                sessions.delete(session.get());
                return;
            }
        }
        if (principal != null) {
            sessions.lockById(principal.sessionId()).ifPresent(sessions::delete);
        }
    }

    private Tokens issue(AuthSession session, Instant now) {
        String access = randomToken();
        String refresh = randomToken();
        session.setAccessHash(hash(access));
        session.setRefreshHash(hash(refresh));
        session.setAccessExpiresAt(now.plus(ACCESS_LIFETIME));
        session.setRefreshExpiresAt(now.plus(REFRESH_LIFETIME));
        sessions.save(session);
        return new Tokens(access, refresh, AuthUser.from(session.getUser()));
    }

    static void validatePassword(String password) {
        if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters and at most 72 UTF-8 bytes");
        }
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException invalidSession() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh session is invalid or expired");
    }

    private static boolean isToken(String value) {
        return value != null && TOKEN.matcher(value).matches();
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Tokens(String access, String refresh, AuthUser user) {}
}
