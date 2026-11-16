package br.com.newelog.auth.model;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registro somente de inclusão; o nome do autor é preservado no momento da mudança. */
@Entity
@Immutable
@Table(name = "historico_perfil")
public class HistoricoPerfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "alterado_por_id", nullable = false, updatable = false)
    private Long alteradoPorId;

    @Column(name = "alterado_por_nome", nullable = false, updatable = false, length = 150)
    private String alteradoPorNome;

    @Column(name = "alterado_em", nullable = false, updatable = false)
    private Instant alteradoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil_anterior", nullable = false, updatable = false, length = 20)
    private Perfil perfilAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil_novo", nullable = false, updatable = false, length = 20)
    private Perfil perfilNovo;

    protected HistoricoPerfil() {
    }

    public HistoricoPerfil(Long usuarioId, Usuario autor, Perfil perfilAnterior, Perfil perfilNovo,
            Instant alteradoEm) {
        this.usuarioId = usuarioId;
        this.alteradoPorId = autor.getId();
        this.alteradoPorNome = autor.getNome();
        this.perfilAnterior = perfilAnterior;
        this.perfilNovo = perfilNovo;
        this.alteradoEm = alteradoEm;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public Long getAlteradoPorId() { return alteradoPorId; }
    public String getAlteradoPorNome() { return alteradoPorNome; }
    public Instant getAlteradoEm() { return alteradoEm; }
    public Perfil getPerfilAnterior() { return perfilAnterior; }
    public Perfil getPerfilNovo() { return perfilNovo; }
}
