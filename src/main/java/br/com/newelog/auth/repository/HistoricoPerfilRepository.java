package br.com.newelog.auth.repository;

import java.util.List;

import org.springframework.data.repository.Repository;

import br.com.newelog.auth.model.HistoricoPerfil;

/** Expõe apenas inclusão e consulta: não há operações de edição ou exclusão. */
public interface HistoricoPerfilRepository extends Repository<HistoricoPerfil, Long> {

    HistoricoPerfil save(HistoricoPerfil historico);

    List<HistoricoPerfil> findByUsuarioIdOrderByAlteradoEmDescIdDesc(Long usuarioId);
}
