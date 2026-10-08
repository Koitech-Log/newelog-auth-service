package br.com.newelog.auth.security;

import br.com.newelog.auth.model.Perfil;

/** Principal colocado no SecurityContext a partir das claims do JWT. */
public record UsuarioAutenticado(Long id, String email, String nome, Perfil perfil) {
}
