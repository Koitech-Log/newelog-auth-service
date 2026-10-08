package br.com.newelog.auth.dto;

import java.time.Instant;

/** perfil sai em minúsculas ("gestor" | "operador") para casar com o tipo Perfil do front. */
public record LoginResponseDTO(
        String token,
        String tipo,
        Instant expiraEm,
        String nome,
        String perfil
) {
}
