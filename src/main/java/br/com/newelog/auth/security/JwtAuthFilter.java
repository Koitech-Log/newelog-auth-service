package br.com.newelog.auth.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.newelog.auth.model.Perfil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecalho = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecalho != null && cabecalho.startsWith(PREFIXO)) {
            jwtService.validar(cabecalho.substring(PREFIXO.length()).trim()).ifPresent(this::autenticar);
        }
        chain.doFilter(request, response);
    }

    private void autenticar(Claims claims) {
        try {
            Perfil perfil = Perfil.valueOf(claims.get("perfil", String.class));
            UsuarioAutenticado principal = new UsuarioAutenticado(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("nome", String.class),
                    perfil);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()))));
        } catch (IllegalArgumentException | NullPointerException e) {
            // claims malformadas: segue sem autenticação e o entry point responde 401
        }
    }
}
