package br.com.newelog.auth.repository;
import java.util.List;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.newelog.auth.model.Usuario;
import br.com.newelog.auth.model.Perfil;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
        select u from Usuario u
        where (:filtrarPerfil = false or u.perfil = :perfil)
          and (
              :busca = ''
              or locate(:busca, lower(u.nome)) > 0
              or locate(:busca, lower(u.email)) > 0
          )
        order by u.nome
        """)
List<Usuario> buscar(
        @Param("busca") String busca,
        @Param("perfil") Perfil perfil,
        @Param("filtrarPerfil") boolean filtrarPerfil
);
}
