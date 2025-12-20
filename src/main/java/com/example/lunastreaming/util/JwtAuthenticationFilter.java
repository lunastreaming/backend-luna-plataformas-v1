package com.example.lunastreaming.util;

import com.example.lunastreaming.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;


@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Bypass preflight OPTIONS
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Bypass rutas públicas (evitar validar en endpoints de auth)
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtTokenProvider.validateToken(token).getBody();
                if (claims != null) {
                    String userId = claims.getSubject();
                    if (userId != null && !userId.isBlank()) {
                        // 1. Extraer el rol del claim
                        String role = claims.get("role", String.class);

                        // 2. Crear la lista de autoridades
                        // Usaremos el prefijo ROLE_ para que funcione con hasRole('seller')
                        List<SimpleGrantedAuthority> authorities = List.of();
                        if (role != null) {
                            authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                        }

                        // 3. Pasar las autoridades al objeto de autenticación
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(userId, null, authorities);

                        SecurityContextHolder.getContext().setAuthentication(auth);
                        log.debug("JWT validado para principal: {} con rol: {}", userId, role);
                    } else {
                        log.debug("JWT válido pero sin subject en claims");
                        SecurityContextHolder.clearContext();
                    }
                } else {
                    log.debug("validateToken devolvió claims nulos");
                    SecurityContextHolder.clearContext();
                }
            } catch (Exception e) {
                // No detengas la cadena; limpias el contexto y dejas que el flujo devuelva 401 si aplica
                SecurityContextHolder.clearContext();
                log.debug("Token JWT inválido o error al validar: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }


}
