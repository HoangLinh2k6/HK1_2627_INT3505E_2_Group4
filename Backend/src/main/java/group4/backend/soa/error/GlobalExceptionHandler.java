package group4.backend.soa.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ObjectProvider<Clock> clockProvider;

    public GlobalExceptionHandler(ObjectProvider<Clock> clockProvider) {
        this.clockProvider = clockProvider;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return respond(ex.getStatusCode(), ex.getCode(), ex.getReason(), request, ex.getFieldErrors(), null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        String code = ApiErrorResponse.defaultCode(ex.getStatusCode().value());
        return respond(ex.getStatusCode(), code, ex.getReason(), request, null, ex.getHeaders());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> errors = extractBindingErrors(ex);
        String detail = summarizeValidationErrors(errors);
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail, request, errors, null);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(BindException ex, HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> errors = extractBindingErrors(ex);
        String detail = summarizeValidationErrors(errors);
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", detail, request, errors, null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException ex,
                                                                   HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String param = result.getMethodParameter().getParameterName();
            String field = param != null ? param : "parameter";
            for (MessageSourceResolvable resolvable : result.getResolvableErrors()) {
                String code = resolvable.getCodes() != null && resolvable.getCodes().length > 0
                        ? resolvable.getCodes()[resolvable.getCodes().length - 1]
                        : "INVALID_VALUE";
                String message = resolvable.getDefaultMessage() != null
                        ? resolvable.getDefaultMessage()
                        : "Invalid value";
                errors.add(ApiErrorResponse.FieldError.of(field, code, message, result.getArgument()));
            }
        });
        errors.sort(Comparator.comparing(ApiErrorResponse.FieldError::field, Comparator.nullsLast(String::compareTo)));
        String detail = summarizeValidationErrors(errors);
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail, request, errors, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> errors = ex.getConstraintViolations().stream()
                .map(this::toFieldError)
                .sorted(Comparator.comparing(ApiErrorResponse.FieldError::field, Comparator.nullsLast(String::compareTo)))
                .toList();
        String detail = summarizeValidationErrors(errors);
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail, request, errors, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        String name = ex.getName();
        Class<?> requiredType = ex.getRequiredType();
        Object value = ex.getValue();
        String message = formatTypeMismatchMessage(name, requiredType, value);
        String fieldCode = requiredType != null && requiredType.isEnum() ? "INVALID_ENUM" : "TYPE_MISMATCH";
        var fieldError = ApiErrorResponse.FieldError.of(name, fieldCode, message, value);
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, request, List.of(fieldError), null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex,
                                                               HttpServletRequest request) {
        String name = ex.getParameterName();
        String detail = "Required query parameter '" + name + "' (" + ex.getParameterType() + ") is missing.";
        var fieldError = ApiErrorResponse.FieldError.of(name, "MISSING_PARAMETER", detail);
        return respond(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", detail, request, List.of(fieldError), null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
                                                                HttpServletRequest request) {
        String header = ex.getHeaderName();
        String detail = "Required HTTP header '" + header + "' is missing.";
        var fieldError = ApiErrorResponse.FieldError.of(header, "MISSING_HEADER", detail);
        return respond(HttpStatus.BAD_REQUEST, "MISSING_HEADER", detail, request, List.of(fieldError), null);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPart(MissingServletRequestPartException ex,
                                                              HttpServletRequest request) {
        String part = ex.getRequestPartName();
        String detail = "Required multipart field '" + part + "' is missing.";
        var fieldError = ApiErrorResponse.FieldError.of(part, "MISSING_PART", detail);
        return respond(HttpStatus.BAD_REQUEST, "MISSING_PART", detail, request, List.of(fieldError), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                 HttpServletRequest request) {
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException invalidFormat) {
            String field = extractJsonPath(invalidFormat);
            Class<?> targetType = invalidFormat.getTargetType();
            Object rejected = invalidFormat.getValue();
            String message = formatTypeMismatchMessage(field != null ? field : "body", targetType, rejected);
            String fieldCode = targetType != null && targetType.isEnum() ? "INVALID_ENUM" : "INVALID_FORMAT";
            List<ApiErrorResponse.FieldError> errors = field != null
                    ? List.of(ApiErrorResponse.FieldError.of(field, fieldCode, message, rejected))
                    : null;
            return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", message, request, errors, null);
        }
        if (cause instanceof MismatchedInputException mismatched) {
            String field = extractJsonPath(mismatched);
            Class<?> targetType = mismatched.getTargetType();
            String detail;
            List<ApiErrorResponse.FieldError> errors = null;
            if (field != null) {
                String expected = describeExpectedType(targetType);
                detail = "Invalid JSON value for field '" + field + "'. Expected " + expected + ".";
                errors = List.of(ApiErrorResponse.FieldError.of(field, "TYPE_MISMATCH", detail));
            } else {
                detail = "Request body is missing or does not match the expected JSON structure.";
            }
            return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", detail, request, errors, null);
        }
        String rawMessage = ex.getMessage();
        String detail = rawMessage != null && rawMessage.contains("Required request body is missing")
                ? "Request body is required and must be valid JSON."
                : "Malformed JSON request body. Check JSON syntax and field types.";
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", detail, request, null, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                                                HttpServletRequest request) {
        String detail = "Uploaded file or multipart request exceeds the maximum allowed size (5 MB per file, 6 MB per request).";
        return respond(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", detail, request, null, null);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartException(MultipartException ex,
                                                                     HttpServletRequest request) {
        String detail = "Request must be a valid multipart/form-data upload.";
        return respond(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART_REQUEST", detail, request, null, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                       HttpServletRequest request) {
        String supported = ex.getSupportedMediaTypes().stream()
                .map(MediaType::toString)
                .collect(Collectors.joining(", "));
        String detail = "Content-Type '" + ex.getContentType() + "' is not supported."
                + (supported.isEmpty() ? "" : " Supported media types: " + supported + ".");
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", detail, request, null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                   HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        String supported = "";
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            headers.setAllow(ex.getSupportedHttpMethods());
            supported = ex.getSupportedHttpMethods().stream()
                    .map(HttpMethod::name)
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
        String detail = "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint."
                + (supported.isEmpty() ? "" : " Supported methods: " + supported + ".");
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", detail, request, null, headers);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
        String detail = "No endpoint or resource found for '" + request.getMethod() + " " + request.getRequestURI() + "'.";
        return respond(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND", detail, request, null, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                HttpServletRequest request) {
        String causeMessage = deepestMessage(ex).toLowerCase(Locale.ROOT);
        if (causeMessage.contains("foreign key") || causeMessage.contains("parent row")
                || causeMessage.contains("referential integrity")) {
            return respond(HttpStatus.CONFLICT, "RESOURCE_IN_USE",
                    "The operation cannot be completed because this resource is still referenced by related records.",
                    request, null, null);
        }
        if (causeMessage.contains("duplicate") || causeMessage.contains("unique")) {
            return respond(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE",
                    "A record with the same unique value already exists.",
                    request, null, null);
        }
        return respond(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "The request violates a database integrity constraint.",
                request, null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error while processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected internal server error occurred. Please try again later.",
                request, null, null);
    }

    private ResponseEntity<ApiErrorResponse> respond(HttpStatusCode status, String code, String detail,
                                                     HttpServletRequest request,
                                                     List<ApiErrorResponse.FieldError> errors,
                                                     HttpHeaders extraHeaders) {
        Instant now = clockProvider.getIfAvailable(Clock::systemUTC).instant();
        ApiErrorResponse body = ApiErrorResponse.of(status, code, detail, request.getRequestURI(), now, errors);
        HttpHeaders headers = new HttpHeaders();
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            headers.putAll(extraHeaders);
        }
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(body, headers, status);
    }

    private List<ApiErrorResponse.FieldError> extractBindingErrors(BindException ex) {
        List<ApiErrorResponse.FieldError> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String field = fieldError.getField();
            String code = fieldError.isBindingFailure() ? "TYPE_MISMATCH"
                    : (fieldError.getCode() != null ? fieldError.getCode() : "INVALID_VALUE");
            String message;
            if (fieldError.isBindingFailure()) {
                Class<?> targetType = ex.getBindingResult().getFieldType(field);
                message = formatTypeMismatchMessage(field, targetType, fieldError.getRejectedValue());
            } else {
                message = fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value";
            }
            errors.add(ApiErrorResponse.FieldError.of(field, code, message, fieldError.getRejectedValue()));
        }
        for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
            String message = globalError.getDefaultMessage() != null ? globalError.getDefaultMessage() : "Invalid request";
            errors.add(ApiErrorResponse.FieldError.of(globalError.getObjectName(),
                    globalError.getCode() != null ? globalError.getCode() : "INVALID_OBJECT", message));
        }
        errors.sort(Comparator.comparing(ApiErrorResponse.FieldError::field, Comparator.nullsLast(String::compareTo))
                .thenComparing(ApiErrorResponse.FieldError::message, Comparator.nullsLast(String::compareTo)));
        return errors;
    }

    private ApiErrorResponse.FieldError toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "parameter";
        int lastDot = path.lastIndexOf('.');
        String field = lastDot >= 0 ? path.substring(lastDot + 1) : path;
        String code = violation.getConstraintDescriptor() != null
                && violation.getConstraintDescriptor().getAnnotation() != null
                ? violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
                : "CONSTRAINT_VIOLATION";
        return ApiErrorResponse.FieldError.of(field, code, violation.getMessage(), violation.getInvalidValue());
    }

    private static String summarizeValidationErrors(List<ApiErrorResponse.FieldError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "Request validation failed.";
        }
        if (errors.size() == 1) {
            var single = errors.getFirst();
            return "Validation failed for '" + single.field() + "': " + single.message();
        }
        String fields = errors.stream()
                .map(ApiErrorResponse.FieldError::field)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(", "));
        return "Validation failed for " + errors.size() + " field(s): " + fields + ".";
    }

    private static String formatTypeMismatchMessage(String name, Class<?> targetType, Object value) {
        String rejected = value == null ? "null" : "'" + value + "'";
        if (targetType != null && targetType.isEnum()) {
            String allowed = Arrays.stream(targetType.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return "Invalid value " + rejected + " for '" + name + "'. Allowed values: " + allowed + ".";
        }
        return "Invalid value " + rejected + " for '" + name + "'. Expected " + describeExpectedType(targetType) + ".";
    }

    private static String describeExpectedType(Class<?> targetType) {
        if (targetType == null) {
            return "a valid value";
        }
        if (targetType == Long.class || targetType == long.class
                || targetType == Integer.class || targetType == int.class) {
            return "an integer";
        }
        if (targetType == BigDecimal.class || targetType == Double.class || targetType == double.class) {
            return "a number";
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return "a boolean (true or false)";
        }
        if (targetType == LocalDate.class) {
            return "an ISO date (YYYY-MM-DD)";
        }
        if (targetType == LocalTime.class) {
            return "an ISO time (HH:mm or HH:mm:ss)";
        }
        if (targetType == LocalDateTime.class || targetType == OffsetDateTime.class || targetType == Instant.class) {
            return "an ISO-8601 date-time";
        }
        if (targetType.isEnum()) {
            return "one of: " + Arrays.stream(targetType.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
        }
        return "a valid " + targetType.getSimpleName();
    }

    private static String extractJsonPath(JacksonException ex) {
        if (ex.getPath() == null || ex.getPath().isEmpty()) {
            return null;
        }
        StringBuilder path = new StringBuilder();
        for (var ref : ex.getPath()) {
            if (ref.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.isEmpty() ? null : path.toString();
    }

    private static String deepestMessage(Throwable throwable) {
        Throwable current = throwable;
        String lastMessage = "";
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                lastMessage = current.getMessage();
            }
            current = current.getCause();
        }
        return lastMessage;
    }
}
