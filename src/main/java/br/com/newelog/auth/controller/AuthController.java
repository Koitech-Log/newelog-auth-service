package br.com.newelog.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.newelog.auth.dto.AlterarSenhaRequestDTO;
import br.com.newelog.auth.dto.LoginRequestDTO;
import br.com.newelog.auth.dto.LoginResponseDTO;
import br.com.newelog.auth.dto.UsuarioDTO;
import br.com.newelog.auth.security.UsuarioAutenticado;
import br.com.newelog.auth.service.AuthService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO dto) {
        return authService.login(dto.email(), dto.senha());
    }

    @GetMapping("/me")
    public UsuarioDTO me(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return authService.me(principal.id());
    }

    @PostMapping("/alterar-senha")
    public ResponseEntity<Void> alterarSenha(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody AlterarSenhaRequestDTO dto
    ) {
        authService.alterarSenha(principal.id(), dto.senhaAtual(), dto.novaSenha());
        return ResponseEntity.noContent().build();
    }
}
