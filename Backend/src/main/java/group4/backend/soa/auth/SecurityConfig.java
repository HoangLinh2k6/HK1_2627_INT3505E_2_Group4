package group4.backend.soa.auth;

import group4.backend.soa.error.ApiErrorResponse;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.http.HttpMethod.*;

@Configuration(proxyBeanMethods = false)
class AuthBeans {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
public class SecurityConfig {
    private static final String AUTH_FAILURE_ATTR = SecurityConfig.class.getName() + ".AUTH_FAILURE";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, AuthService auth,
                                    ObjectProvider<ObjectMapper> objectMapperProvider,
                                    ObjectProvider<Clock> clockProvider) throws Exception {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable(() -> JsonMapper.builder().build());
        return http
                // Only the auth endpoints accept cookies. They check the browser origin separately.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, failure) -> {
                            String reason = (String) request.getAttribute(AUTH_FAILURE_ATTR);
                            String code;
                            String detail;
                            if ("INVALID_ACCESS_TOKEN".equals(reason)) {
                                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
                                code = "INVALID_ACCESS_TOKEN";
                                detail = "The provided Bearer access token is invalid, expired, or has been revoked. Refresh the session or log in again.";
                            } else if ("INVALID_AUTH_HEADER".equals(reason)) {
                                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_request\"");
                                code = "INVALID_AUTH_HEADER";
                                detail = "Authorization header must use the Bearer scheme: 'Authorization: Bearer <accessToken>'.";
                            } else {
                                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                                code = "AUTHENTICATION_REQUIRED";
                                detail = "Authentication is required. Provide a valid Bearer access token in the Authorization header.";
                            }
                            writeProblem(request, response, mapper, clockProvider, HttpStatus.UNAUTHORIZED, code, detail);
                        })
                        .accessDeniedHandler((request, response, failure) -> {
                            var authentication = SecurityContextHolder.getContext().getAuthentication();
                            String detail;
                            if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
                                detail = "Account role " + principal.role() + " does not have permission to access "
                                        + request.getMethod() + " " + request.getRequestURI() + ".";
                            } else {
                                detail = "Access denied for " + request.getMethod() + " " + request.getRequestURI() + ".";
                            }
                            writeProblem(request, response, mapper, clockProvider, HttpStatus.FORBIDDEN, "ACCESS_DENIED", detail);
                        }))
                .addFilterBefore(new BearerFilter(auth), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(routes -> routes
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(GET, "/swagger-ui.html", "/swagger-ui/**", "/openapi.yaml").permitAll()
                        .requestMatchers(POST, "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                        .requestMatchers(GET, "/venues", "/venues/*", "/venues/*/courts", "/venues/*/reviews",
                                "/venues/*/services",
                                "/courts", "/courts/*", "/courts/*/availability", "/courts/*/quote",
                                "/sport-types", "/reviews").permitAll()
                        .requestMatchers(GET, "/users/me").authenticated()
                        .requestMatchers(PUT, "/users/me").authenticated()
                        .requestMatchers(GET, "/users/me/bookings").hasRole("CUSTOMER")
                        .requestMatchers(GET, "/owner/venues", "/owner/venues/*", "/venues/*/bookings").hasRole("OWNER")
                        .requestMatchers(POST, "/venues", "/venues/*/courts").hasRole("OWNER")
                        .requestMatchers(PUT, "/venues/*", "/courts/*").hasRole("OWNER")
                        .requestMatchers(DELETE, "/venues/*", "/courts/*").hasRole("OWNER")
                        .requestMatchers(POST, "/sport-types").hasRole("ADMIN")
                        .requestMatchers(PUT, "/sport-types/*").hasRole("ADMIN")
                        .requestMatchers(DELETE, "/sport-types/*").hasRole("ADMIN")
                        .requestMatchers(POST, "/bookings", "/bookings/*/payments", "/venues/*/reviews").hasRole("CUSTOMER")
                        .requestMatchers(PUT, "/reviews/*").hasRole("CUSTOMER")
                        .requestMatchers(DELETE, "/reviews/*").hasRole("CUSTOMER")
                        .requestMatchers(GET, "/bookings/*", "/bookings/*/payments", "/payments/*")
                                .hasAnyRole("CUSTOMER", "OWNER")
                        .requestMatchers(PATCH, "/bookings/*/confirm", "/bookings/*/complete").hasRole("OWNER")
                        .requestMatchers(PATCH, "/bookings/*/cancel").hasAnyRole("CUSTOMER", "OWNER")
                        .requestMatchers(POST, "/bookings/*/confirm", "/bookings/*/complete").hasRole("OWNER")
                        .requestMatchers(POST, "/bookings/*/cancel").hasAnyRole("CUSTOMER", "OWNER")
                        .requestMatchers(GET, "/venues/*/services/*/assets").hasRole("OWNER")
                        .requestMatchers(POST, "/venues/*/services", "/venues/*/services/*/assets").hasRole("OWNER")
                        .requestMatchers(PUT, "/venues/*/services/*", "/venues/*/services/*/assets/*").hasRole("OWNER")
                        .requestMatchers(POST, "/bookings/*/services", "/rentals/*/photos").hasRole("CUSTOMER")
                        .requestMatchers(POST, "/bookings/*/check-in", "/bookings/*/services/*/deliver").hasRole("OWNER")
                        .requestMatchers(GET, "/bookings/*/services", "/rentals/*/photos", "/rentals/*/photos/*",
                                "/rentals/*/photos/*/link")
                                .hasAnyRole("CUSTOMER", "OWNER")
                        .requestMatchers(DELETE, "/bookings/*/services/*").hasAnyRole("CUSTOMER", "OWNER")
                        .requestMatchers(POST, "/bookings/*/check-out").hasAnyRole("CUSTOMER", "OWNER")
                        .anyRequest().denyAll())
                .build();
    }

    private static void writeProblem(HttpServletRequest request, HttpServletResponse response,
                                     ObjectMapper mapper, ObjectProvider<Clock> clockProvider,
                                     HttpStatus status, String code, String detail) throws IOException {
        Instant now = clockProvider.getIfAvailable(Clock::systemUTC).instant();
        ApiErrorResponse problem = ApiErrorResponse.of(status, code, detail, request.getRequestURI(), now);
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getWriter(), problem);
    }

    private static final class BearerFilter extends OncePerRequestFilter {
        private final AuthService auth;

        private BearerFilter(AuthService auth) {
            this.auth = auth;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()) {
                if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    var authenticated = auth.authenticate(authorization.substring(7));
                    if (authenticated.isPresent()) {
                        var principal = authenticated.get();
                        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        request.setAttribute(AUTH_FAILURE_ATTR, "INVALID_ACCESS_TOKEN");
                    }
                } else {
                    request.setAttribute(AUTH_FAILURE_ATTR, "INVALID_AUTH_HEADER");
                }
            }
            chain.doFilter(request, response);
        }
    }
}
