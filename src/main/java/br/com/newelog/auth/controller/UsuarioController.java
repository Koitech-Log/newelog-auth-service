package br.com.newelog.auth.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.newelog.auth.dto.AtualizarAtivoRequestDTO;
import br.com.newelog.auth.dto.CriarUsuarioRequestDTO;
import br.com.newelog.auth.dto.UsuarioDTO;
import br.com.newelog.auth.security.UsuarioAutenticado;
import br.com.newelog.auth.service.UsuarioService;
import jakarta.validation.Valid;

/** Gestão de usuários: restrita ao perfil GESTOR (ver SecurityConfig). */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<UsuarioDTO> listar() {
        return usuarioService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioDTO criar(@Valid @RequestBody CriarUsuarioRequestDTO dto) {
        return usuarioService.criar(dto);
    }

    @PatchMapping("/{id}/ativo")
    public UsuarioDTO atualizarAtivo(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarAtivoRequestDTO dto,
            @AuthenticationPrincipal UsuarioAutenticado principal
    ) {
        return usuarioService.atualizarAtivo(id, dto.ativo(), principal.id());
    }
}
