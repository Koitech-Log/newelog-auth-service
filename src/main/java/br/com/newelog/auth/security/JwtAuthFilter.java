package br.com.newelog.auth.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.newelog.auth.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
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
            Long id = Long.valueOf(claims.getSubject());
            // perfil vem do BANCO, não do token: a mudança vale na próxima requisição
            usuarioRepository.findById(id).ifPresent(u -> {
                UsuarioAutenticado principal =
                        new UsuarioAutenticado(u.getId(), u.getEmail(), u.getNome(), u.getPerfil());
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + u.getPerfil().name()))));
            });
        } catch (IllegalArgumentException | NullPointerException e) {
            // claims malformadas: segue sem autenticação e o entry point responde 401
        }
    }
}