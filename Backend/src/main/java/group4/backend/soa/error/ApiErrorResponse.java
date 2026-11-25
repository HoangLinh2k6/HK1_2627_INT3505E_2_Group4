package group4.backend.soa.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        Instant timestamp,
        List<FieldError> errors
) {
    public static final String PROBLEM_TYPE_PREFIX = "urn:keosan:problem:";
    private static final int MAX_REJECTED_VALUE_LENGTH = 100;
    private static final Set<String> SENSITIVE_FIELD_KEYWORDS = Set.of(
            "password", "secret", "token", "authorization", "cookie", "credential"
    );

    public static ApiErrorResponse of(HttpStatusCode statusCode, String code, String detail,
                                      String instance, Instant timestamp) {
        return of(statusCode, code, detail, instance, timestamp, null);
    }

    public static ApiErrorResponse of(HttpStatusCode statusCode, String code, String detail,
                                      String instance, Instant timestamp, List<FieldError> errors) {
        int status = statusCode.value();
        String resolvedCode = (code == null || code.isBlank()) ? defaultCode(status) : code;
        String resolvedTitle = resolveTitle(statusCode);
        String resolvedDetail = (detail == null || detail.isBlank()) ? resolvedTitle : detail;
        List<FieldError> resolvedErrors = (errors == null || errors.isEmpty()) ? null : List.copyOf(errors);
        return new ApiErrorResponse(
                PROBLEM_TYPE_PREFIX + resolvedCode.toLowerCase(Locale.ROOT).replace('_', '-'),
                resolvedTitle,
                status,
                resolvedDetail,
                instance,
                resolvedCode,
                timestamp,
                resolvedErrors
        );
    }

    public static String defaultCode(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 422 -> "UNPROCESSABLE_ENTITY";
            case 500 -> "INTERNAL_SERVER_ERROR";
            case 502 -> "BAD_GATEWAY";
            case 503 -> "SERVICE_UNAVAILABLE";
            default -> "HTTP_" + status;
        };
    }

    private static String resolveTitle(HttpStatusCode statusCode) {
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        return resolved != null ? resolved.getReasonPhrase() : "HTTP " + statusCode.value();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldError(
            String field,
            String code,
            String message,
            Object rejectedValue
    ) {
        public static FieldError of(String field, String code, String message) {
            return new FieldError(field, code, message, null);
        }

        public static FieldError of(String field, String code, String message, Object rejectedValue) {
            return new FieldError(field, code, message, sanitize(field, rejectedValue));
        }

        private static Object sanitize(String field, Object value) {
            if (value == null) {
                return null;
            }
            if (field != null) {
                String lower = field.toLowerCase(Locale.ROOT);
                for (String keyword : SENSITIVE_FIELD_KEYWORDS) {
                    if (lower.contains(keyword)) {
                        return null;
                    }
                }
            }
            if (value instanceof Number || value instanceof Boolean) {
                return value;
            }
            if (value instanceof Enum<?> enumValue) {
                return enumValue.name();
            }
            if (value instanceof CharSequence text) {
                String str = text.toString();
                return str.length() <= MAX_REJECTED_VALUE_LENGTH
                        ? str
                        : str.substring(0, MAX_REJECTED_VALUE_LENGTH) + "...";
            }
            return null;
        }
    }
}
