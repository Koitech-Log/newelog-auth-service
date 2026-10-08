package br.com.newelog.auth.service;

import org.springframework.http.HttpStatus;

/** Erro de negócio com o status HTTP que deve ser devolvido ao cliente. */
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
