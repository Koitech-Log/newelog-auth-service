package br.com.newelog.auth.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.newelog.auth.model.HistoricoPerfil;
import br.com.newelog.auth.model.Perfil;
import br.com.newelog.auth.model.Usuario;
import br.com.newelog.auth.repository.HistoricoPerfilRepository;
import br.com.newelog.auth.repository.UsuarioRepository;
import br.com.newelog.auth.security.JwtService;
import br.com.newelog.auth.service.AuthException;
import br.com.newelog.auth.service.UsuarioService;
import jakarta.persistence.EntityManager;

/** Requisições usam transações reais para também verificar commit e rollback. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HistoricoPerfilTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private UsuarioService usuarioService;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @MockitoSpyBean private HistoricoPerfilRepository historicos;

    private Usuario gestor;
    private Usuario gestor2;
    private Usuario operador;

    @BeforeEach
    void preparar() {
        String hash = encoder.encode("senha-segura-1");
        gestor = criar("Gestor", "gestor.historico@example.com", Perfil.GESTOR, hash);
        gestor2 = criar("Outro Gestor", "gestor2.historico@example.com", Perfil.GESTOR, hash);
        operador = criar("Operador", "operador.historico@example.com", Perfil.OPERADOR, hash);
    }

    @AfterEach
    void limpar() {
        // Limpa apenas os dados deste teste, inclusive após o teste de falha forçada.
        List<Long> ids = List.of(gestor.getId(), gestor2.getId(), operador.getId());
        for (Long id : ids) {
            jdbc.update("delete from historico_perfil where usuario_id = ? or alterado_por_id = ?", id, id);
        }
        usuarios.deleteAllById(ids);
    }

    @Test
    void registraCadaMudancaComAutorHorarioEPerfis() throws Exception {
        Instant antes = Instant.now();
        alterar(operador, "gestor", gestor);
        alterar(operador, "operador", gestor2);
        Instant depois = Instant.now();

        List<HistoricoPerfil> registros = historicos.findByUsuarioIdOrderByAlteradoEmDescIdDesc(operador.getId());
        assertEquals(2, registros.size());
        HistoricoPerfil ultimo = registros.getFirst();
        assertEquals(operador.getId(), ultimo.getUsuarioId());
        assertEquals(gestor2.getId(), ultimo.getAlteradoPorId());
        assertEquals("Outro Gestor", ultimo.getAlteradoPorNome());
        assertEquals(Perfil.GESTOR, ultimo.getPerfilAnterior());
        assertEquals(Perfil.OPERADOR, ultimo.getPerfilNovo());
        assertEquals(Perfil.OPERADOR, registros.get(1).getPerfilAnterior());
        assertEquals(Perfil.GESTOR, registros.get(1).getPerfilNovo());
        assertEquals(gestor.getId(), registros.get(1).getAlteradoPorId());
        for (HistoricoPerfil registro : registros) {
            assertFalse(registro.getAlteradoEm().isBefore(antes));
            assertFalse(registro.getAlteradoEm().isAfter(depois));
        }

        mockMvc.perform(get(rota(operador)).header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].usuarioId", is(operador.getId().intValue())))
                .andExpect(jsonPath("$[0].alteradoPorId", is(gestor2.getId().intValue())))
                .andExpect(jsonPath("$[0].alteradoPorNome", is("Outro Gestor")))
                .andExpect(jsonPath("$[0].alteradoEm", notNullValue()))
                .andExpect(jsonPath("$[0].perfilAnterior", is("gestor")))
                .andExpect(jsonPath("$[0].perfilNovo", is("operador")))
                .andExpect(jsonPath("$[0].senhaHash").doesNotExist());
    }

    @Test
    void consultaOrdenaPorDataEDesempataPeloIdSemMisturarUsuarios() throws Exception {
        Instant data = Instant.parse("2026-01-01T12:00:00Z");
        // Insere fora da ordem cronológica para garantir que a consulta ordena pela data.
        HistoricoPerfil novo = historicos.save(new HistoricoPerfil(operador.getId(), gestor,
                Perfil.OPERADOR, Perfil.GESTOR, data));
        historicos.save(new HistoricoPerfil(operador.getId(), gestor,
                Perfil.GESTOR, Perfil.OPERADOR, data.minusSeconds(60)));
        HistoricoPerfil empate = historicos.save(new HistoricoPerfil(operador.getId(), gestor2,
                Perfil.OPERADOR, Perfil.GESTOR, data));
        historicos.save(new HistoricoPerfil(gestor2.getId(), gestor,
                Perfil.GESTOR, Perfil.OPERADOR, data.plusSeconds(60)));

        mockMvc.perform(get(rota(operador)).header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(3)))
                .andExpect(jsonPath("$[0].id", is(empate.getId().intValue())))
                .andExpect(jsonPath("$[1].id", is(novo.getId().intValue())))
                .andExpect(jsonPath("$[2].alteradoEm", is(data.minusSeconds(60).toString())));
    }

    @Test
    void preservaNomeDoAutorMesmoAposRenomearUsuario() throws Exception {
        alterar(operador, "gestor", gestor);
        gestor.setNome("Nome novo");
        usuarios.save(gestor);

        mockMvc.perform(get(rota(operador)).header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alteradoPorNome", is("Gestor")));
    }

    @Test
    void perfilIgualECadastroNaoGeramHistorico() throws Exception {
        alterar(operador, "operador", gestor);
        assertTrue(historicos.findByUsuarioIdOrderByAlteradoEmDescIdDesc(operador.getId()).isEmpty());
        mockMvc.perform(get(rota(gestor)).header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void autoalteracaoNaoGeraHistorico() throws Exception {
        mockMvc.perform(patch("/api/usuarios/{id}/perfil", gestor.getId())
                        .header("Authorization", bearer(gestor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"operador\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(Perfil.GESTOR, usuarios.findById(gestor.getId()).orElseThrow().getPerfil());
        assertTrue(historicos.findByUsuarioIdOrderByAlteradoEmDescIdDesc(gestor.getId()).isEmpty());
    }

    @Test
    void ultimoGestorNaoGeraHistorico() {
        gestor2.setPerfil(Perfil.OPERADOR);
        usuarios.save(gestor2);

        // Chama a regra diretamente: via HTTP o único gestor também cairia na autoalteração.
        AuthException erro = assertThrows(AuthException.class,
                () -> usuarioService.atualizarPerfil(gestor.getId(), Perfil.OPERADOR, gestor2.getId()));
        assertEquals("Não é possível remover o último gestor do sistema.", erro.getMessage());
        assertEquals(Perfil.GESTOR, usuarios.findById(gestor.getId()).orElseThrow().getPerfil());
        assertTrue(historicos.findByUsuarioIdOrderByAlteradoEmDescIdDesc(gestor.getId()).isEmpty());
    }

    @Test
    void falhaAoGravarHistoricoDesfazTambemAMudancaDePerfil() {
        doAnswer(invocacao -> {
            entityManager.persist(invocacao.getArgument(0, HistoricoPerfil.class));
            entityManager.flush(); // força os dois INSERT/UPDATE antes de provocar o rollback
            throw new IllegalStateException("Falha simulada ao registrar histórico");
        }).when(historicos).save(any(HistoricoPerfil.class));

        assertThrows(IllegalStateException.class,
                () -> usuarioService.atualizarPerfil(operador.getId(), Perfil.GESTOR, gestor.getId()));

        // Consultas em novas transações: nenhuma das duas gravações pode ter sobrevivido.
        assertEquals(Perfil.OPERADOR, usuarios.findById(operador.getId()).orElseThrow().getPerfil());
        assertTrue(historicos.findByUsuarioIdOrderByAlteradoEmDescIdDesc(operador.getId()).isEmpty());
    }

    @Test
    void falhaDeBancoRetorna500SemInvalidarASessaoNemSalvarAMudanca() throws Exception {
        doThrow(new DataAccessResourceFailureException("Falha de conexão simulada"))
                .when(historicos).save(any(HistoricoPerfil.class));
        String token = bearer(gestor);
        mockMvc.perform(patch("/api/usuarios/{id}/perfil", operador.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"gestor\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensagem", is("Não foi possível concluir a operação. Tente novamente.")));
        mockMvc.perform(get(rota(operador)).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        assertEquals(Perfil.OPERADOR, usuarios.findById(operador.getId()).orElseThrow().getPerfil());
    }

    @Test
    void consultaExigeGestorETokenValido() throws Exception {
        mockMvc.perform(get(rota(operador))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(rota(operador)).header("Authorization", "Bearer invalido"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(rota(operador)).header("Authorization", bearer(operador)))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/usuarios/9223372036854775807/historico-perfil")
                        .header("Authorization", bearer(gestor)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem", is("Usuário não encontrado.")));
    }

    @Test
    void consultaNaoPermiteEditarOuExcluir() throws Exception {
        alterar(operador, "gestor", gestor);
        Long registroId = historicos.findByUsuarioIdOrderByAlteradoEmDescIdDesc(operador.getId())
                .getFirst().getId();
        String token = bearer(gestor);
        mockMvc.perform(patch(rota(operador)).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(rota(operador)).header("Authorization", token))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(rota(operador) + "/" + registroId).header("Authorization", token))
                .andExpect(status().isNotFound());
        assertEquals(1, historicos.findByUsuarioIdOrderByAlteradoEmDescIdDesc(operador.getId()).size());
    }

    @Test
    void gestorRebaixadoPerdeConsultaMesmoComTokenAntigo() throws Exception {
        String tokenAntigo = bearer(gestor2);
        alterar(gestor2, "operador", gestor);
        mockMvc.perform(get(rota(gestor2)).header("Authorization", tokenAntigo))
                .andExpect(status().isForbidden());
    }

    @Test
    void tentativaDeOperadorOuPerfilInvalidoNaoGeraHistorico() throws Exception {
        mockMvc.perform(patch("/api/usuarios/{id}/perfil", gestor2.getId())
                        .header("Authorization", bearer(operador))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"operador\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/usuarios/{id}/perfil", operador.getId())
                        .header("Authorization", bearer(gestor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"chefe\"}"))
                .andExpect(status().isBadRequest());
        assertTrue(historicos.findByUsuarioIdOrderByAlteradoEmDescIdDesc(gestor2.getId()).isEmpty());
        assertTrue(historicos.findByUsuarioIdOrderByAlteradoEmDescIdDesc(operador.getId()).isEmpty());
    }

    @Test
    void duasRequisicoesSimultaneasNaoDuplicamAMesmaMudanca() throws Exception {
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> {
                prontas.countDown();
                assertTrue(iniciar.await(5, TimeUnit.SECONDS));
                return usuarioService.atualizarPerfil(operador.getId(), Perfil.GESTOR, gestor.getId());
            });
            var segunda = executor.submit(() -> {
                prontas.countDown();
                assertTrue(iniciar.await(5, TimeUnit.SECONDS));
                return usuarioService.atualizarPerfil(operador.getId(), Perfil.GESTOR, gestor2.getId());
            });
            assertTrue(prontas.await(5, TimeUnit.SECONDS));
            iniciar.countDown();
            assertEquals("gestor", primeira.get(10, TimeUnit.SECONDS).perfil());
            assertEquals("gestor", segunda.get(10, TimeUnit.SECONDS).perfil());
        }
        var registros = historicos.findByUsuarioIdOrderByAlteradoEmDescIdDesc(operador.getId());
        assertEquals(1, registros.size());
        assertEquals(Perfil.OPERADOR, registros.getFirst().getPerfilAnterior());
        assertEquals(Perfil.GESTOR, registros.getFirst().getPerfilNovo());
    }

    @Test
    void autorEHorarioEnviadosPeloClienteNaoSubstituemOsDadosDoServidor() throws Exception {
        mockMvc.perform(patch("/api/usuarios/{id}/perfil", operador.getId())
                        .header("Authorization", bearer(gestor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "perfil", "gestor", "alteradoPorId", gestor2.getId(),
                                "alteradoPorNome", "Autor falso", "alteradoEm", "2000-01-01T00:00:00Z"))))
                .andExpect(status().isOk());
        var registro = historicos.findByUsuarioIdOrderByAlteradoEmDescIdDesc(operador.getId()).getFirst();
        assertEquals(gestor.getId(), registro.getAlteradoPorId());
        assertEquals(gestor.getNome(), registro.getAlteradoPorNome());
        assertTrue(registro.getAlteradoEm().isAfter(Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void corpoSemPerfilEUsuarioInexistenteNaoAlteramHistorico() throws Exception {
        mockMvc.perform(patch("/api/usuarios/{id}/perfil", operador.getId())
                        .header("Authorization", bearer(gestor))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/usuarios/9223372036854775807/perfil")
                        .header("Authorization", bearer(gestor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"gestor\"}"))
                .andExpect(status().isNotFound());
        assertEquals(Perfil.OPERADOR, usuarios.findById(operador.getId()).orElseThrow().getPerfil());
        assertTrue(historicos.findByUsuarioIdOrderByAlteradoEmDescIdDesc(operador.getId()).isEmpty());
    }

    private Usuario criar(String nome, String email, Perfil perfil, String hash) {
        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenhaHash(hash);
        usuario.setPerfil(perfil);
        return usuarios.save(usuario);
    }

    private void alterar(Usuario usuario, String perfil, Usuario autor) throws Exception {
        mockMvc.perform(patch("/api/usuarios/{id}/perfil", usuario.getId())
                        .header("Authorization", bearer(autor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("perfil", perfil))))
                .andExpect(status().isOk());
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.gerar(usuario, Instant.now()).valor();
    }

    private String rota(Usuario usuario) {
        return "/api/usuarios/" + usuario.getId() + "/historico-perfil";
    }
}
