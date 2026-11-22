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
 * Baseline sécurité. Trois modes mutuellement exclusifs :
 * <ul>
 *   <li><b>default</b> ({@code jokkorm.security.enabled=true}, profil ≠ test) —
 *       OIDC Keycloak obligatoire, tout endpoint {@code /api/**} exige un Bearer JWT.
 *       {@code /actuator/health,info,prometheus} et Swagger UI restent ouverts.</li>
 *   <li><b>dev-open</b> ({@code jokkorm.security.enabled=false}, profil ≠ test) —
 *       Mode dev local sans Keycloak : TOUT est ouvert, le TenantContextFilter lit
 *       toujours {@code X-Tenant-Id} pour la propagation tenant. **Ne jamais activer en prod.**</li>
 *   <li><b>test</b> (profil {@code test}) — comme dev-open, utilisé par les IT Testcontainers.</li>
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
     * Mode dev-open — activé par {@code jokkorm.security.enabled=false}. Permet de bosser
     * sur le frontend Next.js sans monter encore le flow OIDC (chantier phase 4A). Garde
     * le tenant filter actif pour que le header {@code X-Tenant-Id} continue de propager.
     */
    @Bean
    @Profile("!test")
    @ConditionalOnProperty(name = "jokkorm.security.enabled", havingValue = "false")
    public SecurityFilterChain devOpenFilterChain(HttpSecurity http, TenantContextFilter tenantFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
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
