package br.com.newelog.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.newelog.auth.dto.CriarUsuarioRequestDTO;
import br.com.newelog.auth.dto.HistoricoPerfilDTO;
import br.com.newelog.auth.dto.UsuarioDTO;
import br.com.newelog.auth.model.HistoricoPerfil;
import br.com.newelog.auth.model.Perfil;
import br.com.newelog.auth.model.Usuario;
import br.com.newelog.auth.repository.HistoricoPerfilRepository;
import br.com.newelog.auth.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;
    private final HistoricoPerfilRepository historicoRepository;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder encoder,
            HistoricoPerfilRepository historicoRepository) {
        this.repository = repository;
        this.encoder = encoder;
        this.historicoRepository = historicoRepository;
    }

    @Transactional(readOnly = true)
    public List<HistoricoPerfilDTO> listarHistoricoPerfil(Long id) {
        if (!repository.existsById(id)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");
        }
        return historicoRepository.findByUsuarioIdOrderByAlteradoEmDescIdDesc(id)
                .stream().map(HistoricoPerfilDTO::de).toList();
    }

    @Transactional(readOnly = true)
    public List<UsuarioDTO> listar(String busca, String perfil) {
        String termo = busca.trim().toLowerCase(Locale.ROOT);
        String filtro = perfil.trim();
        Perfil perfilSelecionado = null;

        if (!filtro.isEmpty()) {
            try {
                perfilSelecionado = Perfil.valueOf(filtro.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "Perfil inválido.");
            }
        }

        return repository.buscar(termo, perfilSelecionado, perfilSelecionado != null)
                .stream()
                .map(UsuarioDTO::de)
                .toList();
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
        usuario.setMustChangePassword(true);
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

    @Transactional
    public UsuarioDTO atualizarPerfil(Long id, Perfil novoPerfil, Long solicitanteId) {
        if (id.equals(solicitanteId)) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                "Você não pode alterar o próprio perfil.");
        }
        Usuario usuario = repository.findByIdParaAtualizar(id)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        Perfil perfilAnterior = usuario.getPerfil();
        if (perfilAnterior == novoPerfil) {
            return UsuarioDTO.de(usuario);
        }

        boolean rebaixandoGestorAtivo = usuario.getPerfil() == Perfil.GESTOR
                && novoPerfil == Perfil.OPERADOR
                && usuario.isAtivo();

        if (rebaixandoGestorAtivo && repository.countByPerfilAndAtivo(Perfil.GESTOR, true) <= 1) {
            throw new AuthException(HttpStatus.CONFLICT,
                "Não é possível remover o último gestor do sistema.");
        }
        Usuario autor = repository.findById(solicitanteId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "Autor da alteração não encontrado."));
        usuario.setPerfil(novoPerfil);
        historicoRepository.save(new HistoricoPerfil(id, autor, perfilAnterior, novoPerfil, Instant.now()));
        return UsuarioDTO.de(usuario);
    }
}
