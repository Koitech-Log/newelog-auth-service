package br.com.newelog.auth.controller;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.newelog.auth.model.Perfil;
import br.com.newelog.auth.model.Usuario;
import br.com.newelog.auth.repository.UsuarioRepository;

/** KOI-173: senha inicial e primeiro acesso. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PrimeiroAcessoTest {
    
    private static final String SENHA_GESTOR = "senha-segura-1";
    private static final String GESTOR = "gestor@newelog.com";
    private static final String NOVO = "novo@newelog.com";
    private static final String SENHA_INICIAL = "inicial-123";
    private static final String NOVA_SENHA = "nova-senha-456";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository repository;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ObjectMapper mapper;

    @BeforeEach
    void popular() {
        Usuario gestor = new Usuario();
        gestor.setNome("Gestor");
        gestor.setEmail(GESTOR);
        gestor.setSenhaHash(encoder.encode(SENHA_GESTOR));
        gestor.setPerfil(Perfil.GESTOR);
        repository.save(gestor);
    }

    @Test
    void senhaInicialNaoApareceNasRespostasNemFicaEmTextoPuro() throws Exception {
        ResultActions criacao = cadastrarUsuarioNovo()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
        String corpo = criacao.andReturn().getResponse().getContentAsString();
        assertFalse(corpo.contains(SENHA_INICIAL));

        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(GESTOR, SENHA_GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].senha").doesNotExist())
                .andExpect(jsonPath("$[*].senhaHash").doesNotExist());

        Usuario salvo = repository.findByEmail(NOVO).orElseThrow();
        assertNotEquals(SENHA_INICIAL, salvo.getSenhaHash());
        assertTrue(encoder.matches(SENHA_INICIAL, salvo.getSenhaHash()));
    }

    @Test
    void primeiroLoginExigeTrocaDeSenha() throws Exception {
        cadastrarUsuarioNovo().andExpect(status().isCreated());

        mockMvc.perform(postJson("/api/auth/login", Map.of("email", NOVO, "senha", SENHA_INICIAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword", is(true)));

        // a flag também aparece ao restaurar a sessão (F5)
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(NOVO, SENHA_INICIAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword", is(true)));
    }

    @Test
    void senhaInicialNaoPodeSerReutilizadaNaTroca() throws Exception {
        cadastrarUsuarioNovo().andExpect(status().isCreated());
        String token = bearer(NOVO, SENHA_INICIAL);

        mockMvc.perform(postJson("/api/auth/alterar-senha",
                        Map.of("senhaAtual", SENHA_INICIAL, "novaSenha", SENHA_INICIAL))
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", is("A nova senha deve ser diferente da senha inicial.")));

        // a troca recusada não desativa a flag
        mockMvc.perform(postJson("/api/auth/login", Map.of("email", NOVO, "senha", SENHA_INICIAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword", is(true)));
    }

    @Test
    void depoisDaTrocaOSegundoLoginNaoExigeTrocaEASenhaInicialDeixaDeValer() throws Exception {
        cadastrarUsuarioNovo().andExpect(status().isCreated());

        mockMvc.perform(postJson("/api/auth/alterar-senha",
                        Map.of("senhaAtual", SENHA_INICIAL, "novaSenha", NOVA_SENHA))
                        .header("Authorization", bearer(NOVO, SENHA_INICIAL)))
                .andExpect(status().isNoContent());

        mockMvc.perform(postJson("/api/auth/login", Map.of("email", NOVO, "senha", NOVA_SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword", is(false)));

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(NOVO, NOVA_SENHA)))
                .andExpect(jsonPath("$.mustChangePassword", is(false)));

        mockMvc.perform(postJson("/api/auth/login", Map.of("email", NOVO, "senha", SENHA_INICIAL)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private ResultActions cadastrarUsuarioNovo() throws Exception {
        Map<String, String> corpo = Map.of("nome", "Novo Usuario", "email", NOVO,
                "senha", SENHA_INICIAL, "perfil", "operador");
        return mockMvc.perform(postJson("/api/usuarios", corpo)
                .header("Authorization", bearer(GESTOR, SENHA_GESTOR)));
    }

    private MockHttpServletRequestBuilder postJson(String url, Object corpo) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo));
    }

    private String bearer(String email, String senha) throws Exception {
        String resposta = mockMvc.perform(postJson("/api/auth/login", Map.of("email", email, "senha", senha)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(resposta).get("token").asText();
    }
}