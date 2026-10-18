package com.store.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.common.config.AppProperties;
import com.store.common.error.ErrorResponse;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication
public class SecurityConfig {

    private static final String API = "/api/v1";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, ObjectMapper mapper)
            throws Exception {
        http
                // Stateless JWT; refresh cookie is SameSite=Lax and only accepted on POST /auth/refresh.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/health/**").permitAll()
                        .requestMatchers(API + "/webhooks/**").permitAll()
                        .requestMatchers(API + "/auth/**").permitAll()
                        .requestMatchers(API + "/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, API + "/categories/**", API + "/products/**",
                                API + "/search", API + "/delivery/**", API + "/pages/**", API + "/banners",
                                API + "/meta/**").permitAll()
                        .requestMatchers(API + "/cart/**", API + "/cart").permitAll()
                        .requestMatchers(HttpMethod.POST, API + "/newsletter").permitAll()
                        .requestMatchers(API + "/checkout", API + "/orders/**", API + "/orders", API + "/payments/**",
                                API + "/me/**", API + "/me").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(res.getOutputStream(),
                                    new ErrorResponse("UNAUTHENTICATED", "Please log in", Map.of()));
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(res.getOutputStream(),
                                    new ErrorResponse("FORBIDDEN", "You do not have access to this resource", Map.of()));
                        }))
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
