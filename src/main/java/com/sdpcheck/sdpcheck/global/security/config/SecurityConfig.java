package com.sdpcheck.sdpcheck.global.security.config;

import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtAuthenticationFilter;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import com.sdpcheck.sdpcheck.global.security.jwt.SecurityExceptionHandler;
import jakarta.servlet.DispatcherType;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
                                                   MemberRepository memberRepository,
                                                   SecurityExceptionHandler exceptionHandler) throws Exception{
        var paths = PathPatternRequestMatcher.withDefaults();
        var publicEndpoints = new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, "/api/v1/auth/signup"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/login"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/logout"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/refresh"),
                paths.matcher(HttpMethod.GET, "/api/v1/auth/check-login-id"),
                paths.matcher(HttpMethod.GET, "/actuator/health"),
                paths.matcher(HttpMethod.GET, "/actuator/health/**"));

        http
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
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, memberRepository,
                                exceptionHandler, publicEndpoints),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

}
