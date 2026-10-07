package br.com.newelog.auth.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import br.com.newelog.auth.service.AuthException;

/** Mesmo formato de corpo de erro dos outros serviços: { timestamp, mensagem }. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(AuthException ex) {
        return ResponseEntity.status(ex.getStatus()).body(corpoDeErro(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidacao(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(erro -> erro.getDefaultMessage())
                .orElse("Dados inválidos.");
        return ResponseEntity.badRequest().body(corpoDeErro(mensagem));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleCorpoIlegivel(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(corpoDeErro("Corpo da requisição inválido."));
    }

    // Corrida entre dois cadastros com o mesmo e-mail (a UNIQUE do banco é a garantia final).
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegridade(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(corpoDeErro("Já existe um usuário com este e-mail."));
    }

    private Map<String, Object> corpoDeErro(String mensagem) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("timestamp", Instant.now().toString());
        corpo.put("mensagem", mensagem);
        return corpo;
    }
}
