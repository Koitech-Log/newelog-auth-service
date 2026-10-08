package br.com.newelog.auth.dto;

import java.util.Locale;

import br.com.newelog.auth.model.Usuario;

public record UsuarioDTO(Long id, String nome, String email, String perfil, boolean ativo) {

    public static UsuarioDTO de(Usuario u) {
        return new UsuarioDTO(u.getId(), u.getNome(), u.getEmail(),
                u.getPerfil().name().toLowerCase(Locale.ROOT), u.isAtivo());
    }
}
