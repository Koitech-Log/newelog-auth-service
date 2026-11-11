package br.com.newelog.auth.dto;

import java.time.Instant;
import java.util.Locale;

import br.com.newelog.auth.model.Usuario;

public record UsuarioDTO(
        Long id,
        String nome,
        String email,
        String perfil,
        boolean ativo,
        boolean mustChangePassword,
        Instant criadoEm,
        Instant ultimoLoginEm
) {

    public static UsuarioDTO de(Usuario u) {
        return new UsuarioDTO(u.getId(), u.getNome(), u.getEmail(),
                u.getPerfil().name().toLowerCase(Locale.ROOT), u.isAtivo(),
                u.isMustChangePassword(), u.getCriadoEm(), u.getUltimoLoginEm());
    }
}
