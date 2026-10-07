package br.com.newelog.auth.dto;

import jakarta.validation.constraints.NotNull;

public record AtualizarAtivoRequestDTO(
        @NotNull(message = "Informe se o usuário fica ativo.") Boolean ativo
) {
}
