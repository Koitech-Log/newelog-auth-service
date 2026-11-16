CREATE TABLE historico_perfil (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT NOT NULL REFERENCES usuario(id),
    alterado_por_id     BIGINT NOT NULL REFERENCES usuario(id),
    alterado_por_nome   VARCHAR(150) NOT NULL,
    alterado_em         TIMESTAMP WITH TIME ZONE NOT NULL,
    perfil_anterior     VARCHAR(20) NOT NULL,
    perfil_novo         VARCHAR(20) NOT NULL,
    CONSTRAINT ck_historico_perfil_anterior CHECK (perfil_anterior IN ('GESTOR', 'OPERADOR')),
    CONSTRAINT ck_historico_perfil_novo CHECK (perfil_novo IN ('GESTOR', 'OPERADOR')),
    CONSTRAINT ck_historico_perfil_mudou CHECK (perfil_anterior <> perfil_novo)
);

CREATE INDEX idx_historico_perfil_usuario_data
    ON historico_perfil (usuario_id, alterado_em DESC, id DESC);
