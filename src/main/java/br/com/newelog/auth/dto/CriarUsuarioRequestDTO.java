package br.com.newelog.auth.dto;

import br.com.newelog.auth.model.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarUsuarioRequestDTO(
        @NotBlank(message = "Informe o nome.")
        @Size(max = 150, message = "O nome pode ter no máximo 150 caracteres.") String nome,

        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "E-mail inválido.")
        @Size(max = 160, message = "O e-mail pode ter no máximo 160 caracteres.") String email,

        @NotBlank(message = "Informe a senha.")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String senha,

        @NotNull(message = "Informe o perfil (gestor ou operador).") Perfil perfil
) {
}
