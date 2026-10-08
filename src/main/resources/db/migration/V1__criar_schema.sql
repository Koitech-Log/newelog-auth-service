-- Modelo de dados do auth-service.
-- senha_hash guarda BCrypt (nunca a senha). perfil: GESTOR | OPERADOR.

CREATE TABLE usuario (
    id                 BIGSERIAL PRIMARY KEY,
    nome               VARCHAR(150) NOT NULL,
    email              VARCHAR(160) NOT NULL UNIQUE,
    senha_hash         VARCHAR(100) NOT NULL,
    perfil             VARCHAR(20)  NOT NULL,
    ativo              BOOLEAN      NOT NULL DEFAULT TRUE,
    tentativas_falhas  INTEGER      NOT NULL DEFAULT 0,
    bloqueado_ate      TIMESTAMP WITH TIME ZONE,
    ultimo_login_em    TIMESTAMP WITH TIME ZONE,
    criado_em          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('GESTOR', 'OPERADOR'))
);
