package br.com.newelog.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.com.newelog.auth.model.Perfil;
import br.com.newelog.auth.model.Usuario;
import br.com.newelog.auth.repository.UsuarioRepository;

/** Cria o primeiro gestor (ADMIN_EMAIL / ADMIN_PASSWORD) quando ainda não há nenhum usuário. */
@Component
public class AdminInicialRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicialRunner.class);

    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;
    private final String nome;
    private final String email;
    private final String senha;

    public AdminInicialRunner(
            UsuarioRepository repository,
            PasswordEncoder encoder,
            @Value("${app.bootstrap.admin-nome:Administrador}") String nome,
            @Value("${app.bootstrap.admin-email:}") String email,
            @Value("${app.bootstrap.admin-password:}") String senha
    ) {
        this.repository = repository;
        this.encoder = encoder;
        this.nome = nome;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        if (email.isBlank() || senha.length() < 8) {
            log.warn("Nenhum usuário cadastrado e ADMIN_EMAIL/ADMIN_PASSWORD (mín. 8 caracteres) não definidos: "
                    + "ninguém conseguirá entrar até que o gestor inicial seja criado.");
            return;
        }
        Usuario admin = new Usuario();
        admin.setNome(nome);
        admin.setEmail(email);
        admin.setSenhaHash(encoder.encode(senha));
        admin.setPerfil(Perfil.GESTOR);
        repository.save(admin);
        log.info("Gestor inicial criado: {}", admin.getEmail());
    }
}
