package com.sdpcheck.sdpcheck.global.security.config;

import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtAuthenticationFilter;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import com.sdpcheck.sdpcheck.global.security.jwt.SecurityExceptionHandler;
import jakarta.servlet.DispatcherType;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
                                                   MemberRepository memberRepository,
                                                   SecurityExceptionHandler exceptionHandler,
                                                   UrlBasedCorsConfigurationSource corsConfigurationSource) throws Exception{
        var paths = PathPatternRequestMatcher.withDefaults();
        var publicEndpoints = new OrRequestMatcher(
                paths.matcher(HttpMethod.GET, "/swagger-ui.html"),
                paths.matcher(HttpMethod.GET, "/swagger-ui/**"),
                paths.matcher(HttpMethod.GET, "/v3/api-docs"),
                paths.matcher(HttpMethod.GET, "/v3/api-docs/**"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/signup"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/login"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/logout"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/refresh"),
                paths.matcher(HttpMethod.GET, "/api/v1/auth/check-login-id"),
                paths.matcher(HttpMethod.GET, "/api/v1/auth/kakao/login"),
                paths.matcher(HttpMethod.GET, "/login/oauth2/code/kakao"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/access-token"),
                paths.matcher(HttpMethod.GET, "/actuator/health"),
                paths.matcher(HttpMethod.GET, "/actuator/health/**"));

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic ->httpBasic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(exceptionHandler)
                        .accessDeniedHandler(exceptionHandler))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(publicEndpoints).permitAll()
                        .requestMatchers(paths.matcher("/api/v1/admin/**")).hasRole("ADMIN")
                        .requestMatchers(paths.matcher(HttpMethod.POST, "/api/v1/files")).hasRole("ADMIN")
                        .requestMatchers(paths.matcher(HttpMethod.POST, "/api/v1/auth/onboarding")).authenticated()
                        .requestMatchers(paths.matcher("/api/v1/devices/push-tokens/**")).hasAnyRole("MEMBER", "ADMIN")
                        .anyRequest().hasAnyRole("MEMBER", "ADMIN"))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, memberRepository,
                                exceptionHandler, publicEndpoints),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .map(SecurityConfig::normalizeCorsOrigin)
                .distinct()
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static String normalizeCorsOrigin(String value) {
        URI origin;
        try {
            origin = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("CORS origins must be exact HTTP(S) addresses without paths or wildcards.");
        }
        String path = origin.getRawPath();
        if ((!"https".equalsIgnoreCase(origin.getScheme()) && !"http".equalsIgnoreCase(origin.getScheme()))
                || origin.getHost() == null || origin.getRawUserInfo() != null
                || origin.getRawQuery() != null || origin.getRawFragment() != null
                || (path != null && !path.isEmpty() && !"/".equals(path))
                || origin.getPort() < -1 || origin.getPort() > 65535 || value.contains("*")) {
            throw new IllegalArgumentException("CORS origins must be exact HTTP(S) addresses without paths or wildcards.");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

}
