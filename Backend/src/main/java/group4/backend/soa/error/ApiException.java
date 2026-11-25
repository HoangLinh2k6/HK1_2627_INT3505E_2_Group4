package group4.backend.soa.error;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

public class ApiException extends ResponseStatusException {
    private final String code;
    private final List<ApiErrorResponse.FieldError> fieldErrors;

    public ApiException(HttpStatusCode status, String code, String detail) {
        this(status, code, detail, null, null);
    }

    public ApiException(HttpStatusCode status, String code, String detail, Throwable cause) {
        this(status, code, detail, null, cause);
    }

    public ApiException(HttpStatusCode status, String code, String detail,
                        List<ApiErrorResponse.FieldError> fieldErrors) {
        this(status, code, detail, fieldErrors, null);
    }

    public ApiException(HttpStatusCode status, String code, String detail,
                        List<ApiErrorResponse.FieldError> fieldErrors, Throwable cause) {
        super(status, detail, cause);
        this.code = code;
        this.fieldErrors = fieldErrors == null || fieldErrors.isEmpty() ? null : List.copyOf(fieldErrors);
    }

    public String getCode() {
        return code;
    }

    public List<ApiErrorResponse.FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }

    public static ApiException badRequest(String code, String detail, List<ApiErrorResponse.FieldError> errors) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail, errors);
    }

    public static ApiException badRequest(String code, String detail, ApiErrorResponse.FieldError error) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail, List.of(error));
    }

    public static ApiException unauthorized(String code, String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, detail);
    }

    public static ApiException forbidden(String code, String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, code, detail);
    }

    public static ApiException notFound(String code, String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, code, detail);
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiException unsupportedMediaType(String code, String detail) {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, code, detail);
    }

    public static ApiException badGateway(String code, String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, code, detail);
    }

    public static ApiException serviceUnavailable(String code, String detail) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, detail);
    }
}
