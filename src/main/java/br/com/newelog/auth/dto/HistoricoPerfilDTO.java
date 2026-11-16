package br.com.newelog.auth.dto;

import java.time.Instant;
import java.util.Locale;

import br.com.newelog.auth.model.HistoricoPerfil;

public record HistoricoPerfilDTO(
        Long id,
        Long usuarioId,
        Long alteradoPorId,
        String alteradoPorNome,
        Instant alteradoEm,
        String perfilAnterior,
        String perfilNovo
) {

    public static HistoricoPerfilDTO de(HistoricoPerfil historico) {
        return new HistoricoPerfilDTO(historico.getId(), historico.getUsuarioId(),
                historico.getAlteradoPorId(), historico.getAlteradoPorNome(), historico.getAlteradoEm(),
                historico.getPerfilAnterior().name().toLowerCase(Locale.ROOT),
                historico.getPerfilNovo().name().toLowerCase(Locale.ROOT));
    }
}
