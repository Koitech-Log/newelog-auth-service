package br.com.newelog.auth.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.newelog.auth.dto.CriarUsuarioRequestDTO;
import br.com.newelog.auth.dto.UsuarioDTO;
import br.com.newelog.auth.model.Usuario;
import br.com.newelog.auth.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<UsuarioDTO> listar() {
        return repository.findAll(Sort.by("nome")).stream().map(UsuarioDTO::de).toList();
    }

    @Transactional
    public UsuarioDTO criar(CriarUsuarioRequestDTO dto) {
        String email = Usuario.normalizarEmail(dto.email());
        if (repository.existsByEmail(email)) {
            throw new AuthException(HttpStatus.CONFLICT, "Já existe um usuário com este e-mail.");
        }
        Usuario usuario = new Usuario();
        usuario.setNome(dto.nome().trim());
        usuario.setEmail(email);
        usuario.setSenhaHash(encoder.encode(dto.senha()));
        usuario.setPerfil(dto.perfil());
        return UsuarioDTO.de(repository.save(usuario));
    }

    @Transactional
    public UsuarioDTO atualizarAtivo(Long id, boolean ativo, Long solicitanteId) {
        if (id.equals(solicitanteId) && !ativo) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Você não pode desativar o próprio usuário.");
        }
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
        usuario.setAtivo(ativo);
        return UsuarioDTO.de(usuario);
    }
}
