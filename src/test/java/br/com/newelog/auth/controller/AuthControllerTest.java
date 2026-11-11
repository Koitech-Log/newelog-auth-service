package br.com.newelog.auth.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.newelog.auth.model.Perfil;
import br.com.newelog.auth.model.Usuario;
import br.com.newelog.auth.repository.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    private static final String SENHA = "senha-segura-1";
    private static final String GESTOR = "gestor@newelog.com";
    private static final String OPERADOR = "operador@newelog.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository repository;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ObjectMapper mapper;

    private Usuario operador;

    @BeforeEach
    void popular() {
        criar(GESTOR, Perfil.GESTOR);
        operador = criar(OPERADOR, Perfil.OPERADOR);
    }

    @Test
    void loginValidoRetornaTokenEPerfilEmMinusculas() throws Exception {
        mockMvc.perform(postJson("/api/auth/login", Map.of("email", GESTOR, "senha", SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.tipo", is("Bearer")))
                .andExpect(jsonPath("$.perfil", is("gestor")));
    }

    @Test
    void emailNaoEhSensivelAMaiusculasEEspacos() throws Exception {
        mockMvc.perform(postJson("/api/auth/login", Map.of("email", "  GESTOR@Newelog.com ", "senha", SENHA)))
                .andExpect(status().isOk());
    }

    @Test
    void senhaErradaRetorna401() throws Exception {
        mockMvc.perform(postJson("/api/auth/login", Map.of("email", GESTOR, "senha", "errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem", is("E-mail ou senha incorretos.")));
    }

    @Test
    void emailInexistenteRetornaMesmaMensagemDeSenhaErrada() throws Exception {
        mockMvc.perform(postJson("/api/auth/login", Map.of("email", "ninguem@newelog.com", "senha", SENHA)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem", is("E-mail ou senha incorretos.")));
    }

    @Test
    void cincoFalhasBloqueiamAContaMesmoComSenhaCorreta() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(postJson("/api/auth/login", Map.of("email", OPERADOR, "senha", "errada")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(postJson("/api/auth/login", Map.of("email", OPERADOR, "senha", "errada")))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(postJson("/api/auth/login", Map.of("email", OPERADOR, "senha", SENHA)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void contaDesativadaRetorna403ComSenhaCorreta() throws Exception {
        operador.setAtivo(false);
        repository.save(operador);
        mockMvc.perform(postJson("/api/auth/login", Map.of("email", OPERADOR, "senha", SENHA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void camposVaziosRetornam400() throws Exception {
        mockMvc.perform(postJson("/api/auth/login", Map.of("email", "", "senha", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meExigeTokenValido() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer token.invalido.aqui"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(GESTOR)))
                .andExpect(jsonPath("$.perfil", is("gestor")));
    }

    @Test
    void operadorNaoAcessaUsuariosMasGestorAcessa() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(OPERADOR)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)));
    }

    @Test
    void gestorCriaUsuarioEEmailDuplicadoRetorna409() throws Exception {
        Map<String, String> novo = Map.of("nome", "Maria", "email", "maria@newelog.com",
                "senha", "outra-senha-1", "perfil", "operador");

        mockMvc.perform(postJson("/api/usuarios", novo).header("Authorization", bearer(GESTOR)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil", is("operador")))
                .andExpect(jsonPath("$.ativo", is(true)))
                .andExpect(jsonPath("$.mustChangePassword", is(true)))
                .andExpect(jsonPath("$.criadoEm", notNullValue()));
        mockMvc.perform(postJson("/api/usuarios", novo).header("Authorization", bearer(GESTOR)))
                .andExpect(status().isConflict());
    }

    @Test
    void operadorNaoPodeCadastrarUsuario() throws Exception {
        Map<String, String> novo = Map.of("nome", "Carlos", "email", "carlos@newelog.com",
                "senha", "outra-senha-1", "perfil", "operador");

        mockMvc.perform(postJson("/api/usuarios", novo).header("Authorization", bearer(OPERADOR)))
                .andExpect(status().isForbidden());
        mockMvc.perform(postJson("/api/usuarios", novo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gestorNaoPodeDesativarASiMesmo() throws Exception {
        Long idGestor = repository.findByEmail(GESTOR).orElseThrow().getId();
        mockMvc.perform(patch("/api/usuarios/" + idGestor + "/ativo")
                        .header("Authorization", bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alterarSenhaValeParaOProximoLogin() throws Exception {
        mockMvc.perform(postJson("/api/auth/alterar-senha", Map.of("senhaAtual", SENHA, "novaSenha", "nova-senha-123"))
                        .header("Authorization", bearer(OPERADOR)))
                .andExpect(status().isNoContent());

        mockMvc.perform(postJson("/api/auth/login", Map.of("email", OPERADOR, "senha", SENHA)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(postJson("/api/auth/login", Map.of("email", OPERADOR, "senha", "nova-senha-123")))
                .andExpect(status().isOk());
    }

    @Test
void validaBuscaFiltrosEPermissaoNaListagemDeUsuarios() throws Exception {
    String emailAna = "ana.koi176@example.com";
    String emailBruno = "bruno.koi176@example.com";

    Usuario ana = criar(emailAna, Perfil.OPERADOR);
    ana.setNome("Ana Pioneira");
    repository.save(ana);

    Usuario bruno = criar(emailBruno, Perfil.GESTOR);
    bruno.setNome("Bruno Pioneira");
    repository.save(bruno);

    String tokenGestor = bearer(GESTOR);

    // Busca por parte do nome, com espaços e letras maiúsculas.
    mockMvc.perform(get("/api/usuarios")
                    .header("Authorization", tokenGestor)
                    .param("busca", "  PIONEIRA  "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(2)))
            .andExpect(jsonPath("$[*].email", hasItem(emailAna)))
            .andExpect(jsonPath("$[*].email", hasItem(emailBruno)));

    // Busca por um trecho que aparece apenas no e-mail.
    mockMvc.perform(get("/api/usuarios")
                    .header("Authorization", tokenGestor)
                    .param("busca", "ana.koi176@"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].email", is(emailAna)));

    // Filtro de perfil sem texto de busca.
    mockMvc.perform(get("/api/usuarios")
                    .header("Authorization", tokenGestor)
                    .param("perfil", "operador"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].email", hasItem(emailAna)))
            .andExpect(jsonPath("$[*].perfil", everyItem(is("operador"))));

    // Os dois nomes correspondem à busca, mas só Ana é operadora.
    mockMvc.perform(get("/api/usuarios")
                    .header("Authorization", tokenGestor)
                    .param("busca", "pioneira")
                    .param("perfil", "operador"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].email", is(emailAna)));

    // Busca sem correspondência retorna uma lista vazia.
    mockMvc.perform(get("/api/usuarios")
                    .header("Authorization", tokenGestor)
                    .param("busca", "sem-correspondencia-koi176"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(0)));

    // Um operador autenticado recebe 403 mesmo enviando os filtros.
    mockMvc.perform(get("/api/usuarios")
                    .header("Authorization", bearer(OPERADOR))
                    .param("busca", "pioneira")
                    .param("perfil", "operador"))
            .andExpect(status().isForbidden());
}

    // ---------- helpers ----------

    private Usuario criar(String email, Perfil perfil) {
        Usuario u = new Usuario();
        u.setNome(email);
        u.setEmail(email);
        u.setSenhaHash(encoder.encode(SENHA));
        u.setPerfil(perfil);
        return repository.save(u);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(String url, Object corpo)
            throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo));
    }

    private String bearer(String email) throws Exception {
        String resposta = mockMvc.perform(postJson("/api/auth/login", Map.of("email", email, "senha", SENHA)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(resposta).get("token").asText();
    }
}
