package com.sdpcheck.sdpcheck.global.security.jwt;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.global.exception.ErrorCode;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper objectMapper;
    // Preserve existing API error codes while honoring the schedule mutation contract.
    private static final RequestMatcher SCHEDULE_MUTATION = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PATCH, "/api/v1/admin/schedules/{scheduleId}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.DELETE, "/api/v1/admin/schedules/{scheduleId}"));

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        writeError(response, SCHEDULE_MUTATION.matches(request) ? AuthErrorCode.UNAUTHENTICATED
                : exception instanceof BadCredentialsException
                    ? AuthErrorCode.INVALID_TOKEN : AuthErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        writeError(response, SCHEDULE_MUTATION.matches(request)
                ? AuthErrorCode.FORBIDDEN : AuthErrorCode.ACCESS_DENIED);
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(errorCode));
    }
}
