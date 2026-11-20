package br.com.newelog.auth.dto;

import br.com.newelog.auth.model.Perfil;
import jakarta.validation.constraints.NotNull;

public record AtualizarPerfilRequestDTO(
        @NotNull(message = "Informe o perfil (gestor ou operador).") Perfil perfil
) {
}