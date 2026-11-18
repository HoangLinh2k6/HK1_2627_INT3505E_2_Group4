package group4.backend.soa.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "keosan_refresh";
    private final AuthService auth;
    private final boolean secureCookie;
    private final String cookiePath;
    private final Set<String> allowedOrigins;

    public AuthController(AuthService auth,
                          @Value("${app.auth.secure-cookie:true}") boolean secureCookie,
                          @Value("${app.auth.cookie-path:/}") String cookiePath,
                          @Value("${app.auth.allowed-origins:}") String allowedOrigins) {
        this.auth = auth;
        this.secureCookie = secureCookie;
        this.cookiePath = cookiePath;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::strip).filter(origin -> !origin.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    @PostMapping("/register")
    public ResponseEntity<Data<AuthUser>> register(@Valid @RequestBody Registration input) {
        var user = auth.register(input.email(), input.password(), input.fullName(), input.phone());
        return ResponseEntity.status(HttpStatus.CREATED).body(new Data<>(AuthUser.from(user)));
    }

    @PostMapping("/login")
    public ResponseEntity<Data<Session>> login(@Valid @RequestBody Credentials input, HttpServletRequest request) {
        checkOrigin(request);
        return sessionResponse(auth.login(input.email(), input.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Data<Session>> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                                HttpServletRequest request) {
        checkOrigin(request);
        return sessionResponse(auth.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal,
                                      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                      HttpServletRequest request) {
        checkOrigin(request);
        auth.logout(principal, refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString()).build();
    }

    private void checkOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = (scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80);
        String serverOrigin = scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
        if ("cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))
                || (origin != null && !serverOrigin.equals(origin) && !allowedOrigins.contains(origin))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Origin not allowed");
        }
    }

    private ResponseEntity<Data<Session>> sessionResponse(AuthService.Tokens tokens) {
        var session = new Session(tokens.access(), "Bearer", AuthService.ACCESS_LIFETIME.toSeconds(), tokens.user());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refresh(), AuthService.REFRESH_LIFETIME).toString())
                .body(new Data<>(session));
    }

    private ResponseCookie refreshCookie(String value, Duration lifetime) {
        return ResponseCookie.from(REFRESH_COOKIE, value).path(cookiePath).httpOnly(true)
                .secure(secureCookie).sameSite("Strict").maxAge(lifetime).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Registration(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(min = 8, max = 72) String password,
                               @NotBlank @Size(max = 255) String fullName,
                               @NotBlank @Pattern(regexp = "0[0-9]{9}") String phone) {}

    public record Credentials(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Size(max = 72) String password) {}

    public record Session(String accessToken, String tokenType, long expiresIn, AuthUser user) {}

    public record Data<T>(T data) {}
}
