package br.com.newelog.auth.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.newelog.auth.dto.LoginResponseDTO;
import br.com.newelog.auth.dto.UsuarioDTO;
import br.com.newelog.auth.model.Usuario;
import br.com.newelog.auth.repository.UsuarioRepository;
import br.com.newelog.auth.security.JwtService;

@Service
public class AuthService {

    static final int MAX_TENTATIVAS = 5;
    static final Duration TEMPO_BLOQUEIO = Duration.ofMinutes(15);
    private static final String MSG_CREDENCIAIS = "E-mail ou senha incorretos.";

    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    // Usado quando o e-mail não existe, para o tempo de resposta não revelar se a conta existe.
    private final String hashFicticio;

    public AuthService(UsuarioRepository repository, PasswordEncoder encoder, JwtService jwtService) {
        this.repository = repository;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.hashFicticio = encoder.encode("senha-ficticia-para-equalizar-tempo");
    }

    // noRollbackFor: o contador de falhas e o bloqueio precisam ser gravados mesmo quando lançamos o erro.
    @Transactional(noRollbackFor = AuthException.class)
    public LoginResponseDTO login(String email, String senha) {
        Instant agora = Instant.now();
        Usuario usuario = repository.findByEmail(Usuario.normalizarEmail(email)).orElse(null);

        if (usuario == null) {
            encoder.matches(senha, hashFicticio);
            throw new AuthException(HttpStatus.UNAUTHORIZED, MSG_CREDENCIAIS);
        }

        if (usuario.getBloqueadoAte() != null && usuario.getBloqueadoAte().isAfter(agora)) {
            throw bloqueado(usuario.getBloqueadoAte(), agora);
        }

        if (!encoder.matches(senha, usuario.getSenhaHash())) {
            usuario.setTentativasFalhas(usuario.getTentativasFalhas() + 1);
            if (usuario.getTentativasFalhas() >= MAX_TENTATIVAS) {
                usuario.setBloqueadoAte(agora.plus(TEMPO_BLOQUEIO));
                usuario.setTentativasFalhas(0);
                repository.save(usuario);
                throw bloqueado(usuario.getBloqueadoAte(), agora);
            }
            repository.save(usuario);
            throw new AuthException(HttpStatus.UNAUTHORIZED, MSG_CREDENCIAIS);
        }

        // Só revela que a conta está desativada depois de a senha estar correta.
        if (!usuario.isAtivo()) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Conta desativada. Procure um gestor.");
        }

        usuario.setTentativasFalhas(0);
        usuario.setBloqueadoAte(null);
        usuario.setUltimoLoginEm(agora);
        repository.save(usuario);

        JwtService.Token token = jwtService.gerar(usuario, agora);
        UsuarioDTO dto = UsuarioDTO.de(usuario);
        return new LoginResponseDTO(token.valor(), "Bearer", token.expiraEm(), dto.nome(), dto.perfil());
    }

    @Transactional(readOnly = true)
    public UsuarioDTO me(Long id) {
        return UsuarioDTO.de(usuarioAtivo(id));
    }

    @Transactional
    public void alterarSenha(Long id, String senhaAtual, String novaSenha) {
        Usuario usuario = usuarioAtivo(id);
        if (!encoder.matches(senhaAtual, usuario.getSenhaHash())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Senha atual incorreta.");
        }
        if (senhaAtual.equals(novaSenha)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "A nova senha deve ser diferente da atual.");
        }
        usuario.setSenhaHash(encoder.encode(novaSenha));
    }

    private Usuario usuarioAtivo(Long id) {
        return repository.findById(id)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED,
                        "Sessão ausente ou expirada. Faça login novamente."));
    }

    private AuthException bloqueado(Instant ate, Instant agora) {
        long minutos = Math.max(1, (long) Math.ceil(Duration.between(agora, ate).toSeconds() / 60.0));
        return new AuthException(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas. Tente novamente em " + minutos + (minutos == 1 ? " minuto." : " minutos."));
    }
}
