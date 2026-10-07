package br.com.newelog.auth.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 401/403 no mesmo formato de erro dos demais serviços: { timestamp, mensagem }. */
public class RespostaErroSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    public RespostaErroSeguranca(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        escrever(response, 401, "Sessão ausente ou expirada. Faça login novamente.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        escrever(response, 403, "Você não tem permissão para esta ação.");
    }

    private void escrever(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), Map.of("timestamp", Instant.now().toString(), "mensagem", mensagem));
    }
}
