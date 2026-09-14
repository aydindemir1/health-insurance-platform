package com.aydindemir.health.policy.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                response, objectMapper, HttpStatus.UNAUTHORIZED,
                                "Authentication required", "A valid bearer token is required"))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(
                                response, objectMapper, HttpStatus.FORBIDDEN,
                                "Operation not permitted",
                                "The authenticated user does not have the required role")))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakRolesConverter())))
                .build();
    }

    private void writeProblem(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String title,
            String detail) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.health-insurance.example/problems/" +
                title.toLowerCase().replace(' ', '-')));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    Converter<Jwt, AbstractAuthenticationToken> keycloakRolesConverter() {
        return jwt -> {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            Object rolesClaim = realmAccess == null ? null : realmAccess.get("roles");
            Collection<?> rawRoles = rolesClaim instanceof Collection<?> roles
                    ? roles
                    : Collections.emptyList();
            var authorities = rawRoles.stream()
                    .map(Object::toString)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }
}
