package com.nickspicks.api.security;

import com.nickspicks.api.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Supabase issues the tokens; this service only verifies them.
 *
 * <p>The project signs with an asymmetric ES256 key, so there is no shared
 * secret to distribute - the public half is fetched from the project's JWKS
 * endpoint and cached by Spring.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppProperties properties;

    public SecurityConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Bearer tokens only - there is no cookie session to forge.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/meta", "/actuator/health", "/actuator/info").permitAll()
                        // Called by Supabase pg_cron, which carries no Supabase user session -
                        // CronController authenticates the caller itself via a shared-secret
                        // header instead of a JWT.
                        .requestMatchers("/api/cron/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * Wraps Spring's default decoder to also assert the issuer and audience.
     * Without this, any validly-signed token from any Supabase project this
     * JWKS happens to serve would be accepted.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        String issuer = properties.getSupabase().getUrl() + "/auth/v1";

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(issuer + "/.well-known/jwks.json")
                .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES256)
                .build();

        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> validator =
                new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        new AudienceValidator(List.of("authenticated")));

        decoder.setJwtValidator(validator);
        return decoder;
    }
}
