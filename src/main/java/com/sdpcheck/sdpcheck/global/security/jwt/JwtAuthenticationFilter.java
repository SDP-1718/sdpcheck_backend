package com.sdpcheck.sdpcheck.global.security.jwt;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;
    private final MemberRepository memberRepository;
    private final SecurityExceptionHandler exceptionHandler;
    private final RequestMatcher publicEndpoints;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return publicEndpoints.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                throw new BadCredentialsException("Bearer token required");
            }

            Long memberId = jwtProvider.getAccessTokenMemberId(authorization.substring(7));
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new BadCredentialsException("Member no longer exists"));

            AuthenticatedMember principal = new AuthenticatedMember(
                    memberId, member.getLoginId(), member.getRole());
            var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (JwtException | IllegalArgumentException | BadCredentialsException exception) {
            SecurityContextHolder.clearContext();
            exceptionHandler.commence(request, response,
                    new BadCredentialsException("Invalid authentication token", exception));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
