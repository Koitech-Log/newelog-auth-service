package br.com.newelog.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "Informe seu e-mail.") String email,
        @NotBlank(message = "Informe sua senha.") String senha
) {
}
