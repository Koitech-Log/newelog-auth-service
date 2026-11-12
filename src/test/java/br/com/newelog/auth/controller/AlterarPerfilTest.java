package br.com.newelog.auth.controller;

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.newelog.auth.model.Perfil;
import br.com.newelog.auth.model.Usuario;
import br.com.newelog.auth.repository.UsuarioRepository;
import br.com.newelog.auth.service.AuthException;
import br.com.newelog.auth.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AlterarPerfilTest {

    private static final String SENHA = "senha-segura-1";
    private static final String GESTOR = "gestor@newelog.com";
    private static final String GESTOR2 = "gestor2@newelog.com";
    private static final String OPERADOR = "operador@newelog.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository repository;
    @Autowired private UsuarioService usuarioService;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ObjectMapper mapper;

    private Usuario gestor;
    private Usuario gestor2;
    private Usuario operador;

    @BeforeEach
    void popular() {
        gestor = criar("Gestor", GESTOR, Perfil.GESTOR);
        gestor2 = criar("Gestor Dois", GESTOR2, Perfil.GESTOR);
        operador = criar("Operador", OPERADOR, Perfil.OPERADOR);
    }

    @Test
    void gestorAlteraPerfilDeOutroUsuario() throws Exception {
        mockMvc.perform(patchPerfil(operador.getId(), "gestor")
                        .header("Authorization", bearer(GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil", is("gestor")));
    }

    @Test
    void mudancaVale_naProximaAcao_semNovoLogin() throws Exception {
        String tokenOperador = bearer(OPERADOR);

        mockMvc.perform(get("/api/usuarios").header("Authorization", tokenOperador))
                .andExpect(status().isForbidden());

        mockMvc.perform(patchPerfil(operador.getId(), "gestor")
                        .header("Authorization", bearer(GESTOR)))
                .andExpect(status().isOk());

        // MESMO token, sem novo login
        mockMvc.perform(get("/api/usuarios").header("Authorization", tokenOperador))
                .andExpect(status().isOk());
    }

    @Test
    void gestorNaoAlteraOProprioPerfil() throws Exception {
        mockMvc.perform(patchPerfil(gestor.getId(), "operador")
                        .header("Authorization", bearer(GESTOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", is("Você não pode alterar o próprio perfil.")));

        assertEquals(Perfil.GESTOR, repository.findById(gestor.getId()).orElseThrow().getPerfil());
    }

        @Test
    void naoRemoveOUltimoGestor() {
        // deixa só um gestor, independente de outros usuários que existam no banco
        repository.findAll().stream()
                .filter(u -> u.getPerfil() == Perfil.GESTOR && !u.getId().equals(gestor.getId()))
                .forEach(u -> {
                    u.setPerfil(Perfil.OPERADOR);
                    repository.save(u);
                });
        repository.flush();

        AuthException erro = assertThrows(AuthException.class,
                () -> usuarioService.atualizarPerfil(gestor.getId(), Perfil.OPERADOR, 999999L));

        assertEquals("Não é possível remover o último gestor do sistema.", erro.getMessage());
        assertEquals(Perfil.GESTOR, repository.findById(gestor.getId()).orElseThrow().getPerfil());
    }

    @Test
    void operadorNaoPodeAlterarPerfil() throws Exception {
        mockMvc.perform(patchPerfil(gestor.getId(), "operador")
                        .header("Authorization", bearer(OPERADOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void perfilInvalidoRetorna400() throws Exception {
        mockMvc.perform(patchPerfil(operador.getId(), "chefe")
                        .header("Authorization", bearer(GESTOR)))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private Usuario criar(String nome, String email, Perfil perfil) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(email);
        u.setSenhaHash(encoder.encode(SENHA));
        u.setPerfil(perfil);
        return repository.save(u);
    }

    private MockHttpServletRequestBuilder patchPerfil(Long id, String perfil) throws Exception {
        return patch("/api/usuarios/" + id + "/perfil")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("perfil", perfil)));
    }

    private String bearer(String email) throws Exception {
        String resposta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "senha", SENHA))))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(resposta).get("token").asText();
    }
}