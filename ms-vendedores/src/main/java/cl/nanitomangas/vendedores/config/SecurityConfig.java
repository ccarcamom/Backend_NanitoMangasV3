package cl.nanitomangas.vendedores.config;

import cl.nanitomangas.vendedores.exception.GlobalExceptionHandler;
import cl.nanitomangas.vendedores.security.JwtFilter;
import cl.nanitomangas.vendedores.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http, JwtUtil jwtUtil, ObjectMapper mapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // /internal/** se protege con X-Internal-Key en el controlador
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/productos/mios").hasRole("VENDEDOR")
                        .requestMatchers(HttpMethod.GET, "/api/productos", "/api/productos/*").permitAll()
                        .requestMatchers("/api/productos", "/api/productos/*").hasAnyRole("VENDEDOR", "ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> escribir(res, mapper, 401, "UNAUTHORIZED", "Token ausente o inválido"))
                        .accessDeniedHandler((req, res, ex) -> escribir(res, mapper, 403, "FORBIDDEN", "Rol sin permiso")))
                .addFilterBefore(new JwtFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void escribir(jakarta.servlet.http.HttpServletResponse res, ObjectMapper mapper, int status,
                                 String error, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getWriter(), GlobalExceptionHandler.body(status, error, message));
    }
}
