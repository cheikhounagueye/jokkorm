package com.jokkorm.config;

import com.jokkorm.commons.web.HttpTenantResolver;
import com.jokkorm.commons.web.TenantContextFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Baseline sécurité. Phase 1 — minimal :
 * <ul>
 *   <li>{@code /actuator/health,info,prometheus} ouverts.</li>
 *   <li>Swagger UI ouvert (sera gated en phase 3 pour prod).</li>
 *   <li>Tout le reste exige un JWT OIDC valide.</li>
 *   <li>{@link TenantContextFilter} résout le tenant AVANT l'auth contrôleur.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public HttpTenantResolver httpTenantResolver() {
        return new HttpTenantResolver();
    }

    @Bean
    public TenantContextFilter tenantContextFilter(HttpTenantResolver resolver) {
        return new TenantContextFilter(resolver);
    }

    @Bean
    @Profile("!test")
    @ConditionalOnProperty(name = "jokkorm.security.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain filterChain(HttpSecurity http, TenantContextFilter tenantFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
            .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Profil {@code test} : désactive OIDC (pas de serveur Keycloak en IT), laisse passer
     * mais applique quand même le tenant filter pour les tests de propagation. L'IT dédié
     * active le tenant via le header {@code X-Tenant-Id}.
     */
    @Bean
    @Profile("test")
    public SecurityFilterChain testFilterChain(HttpSecurity http, TenantContextFilter tenantFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
