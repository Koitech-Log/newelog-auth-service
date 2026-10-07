# newelog-auth-service

Autenticação e gestão de usuários do Newelog Frota. Emite JWT (HS256) para o front e para os demais serviços.

**Stack:** Java 21, Spring Boot 3.4.1, Spring Security, JPA, Flyway, PostgreSQL 16, jjwt 0.12.

## Rodando

```bash
cp .env.example .env   # preencha JWT_SECRET (mín. 32 caracteres), ADMIN_EMAIL e ADMIN_PASSWORD
docker compose up --build
```

Sobe o serviço em `http://localhost:8082` e o Postgres em `localhost:5433`. No primeiro start, com a tabela vazia, o gestor inicial é criado a partir de `ADMIN_EMAIL`/`ADMIN_PASSWORD`. Troque essa senha depois do primeiro login.

Testes (H2 em memória, sem Postgres): `./mvnw clean verify`.

## Variáveis de ambiente

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | sim | – | Conexão PostgreSQL |
| `JWT_SECRET` | sim | – | Segredo HS256 (mín. 32 caracteres) |
| `JWT_EXPIRATION_MINUTES` | não | 480 | Validade do token |
| `FRONTEND_URL` | não | `http://localhost:5173` | Origem liberada no CORS |
| `ADMIN_NOME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | não | – | Gestor inicial |
| `PORT` | não | 8082 | Porta HTTP |

## API

Erros seguem o padrão dos outros serviços: `{ "timestamp": "...", "mensagem": "..." }`.

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/api/auth/login` | público | `{ email, senha }` → `{ token, tipo, expiraEm, nome, perfil }` |
| GET | `/api/auth/me` | autenticado | Dados do usuário do token (consulta o banco) |
| POST | `/api/auth/alterar-senha` | autenticado | `{ senhaAtual, novaSenha }` → 204 |
| GET | `/api/usuarios` | gestor | Lista usuários |
| POST | `/api/usuarios` | gestor | `{ nome, email, senha, perfil }` → 201 |
| PATCH | `/api/usuarios/{id}/ativo` | gestor | `{ ativo }` |

`perfil` é `"gestor"` ou `"operador"` (minúsculas), igual ao tipo `Perfil` do front.

Login: 401 credenciais incorretas, 403 conta desativada, 429 bloqueio. Após 5 senhas erradas seguidas a conta fica bloqueada por 15 minutos.

## Validando o token nos outros serviços

O token é um JWT HS256 com `iss = newelog-auth-service`, `sub = id do usuário` e as claims `email`, `nome` e `perfil` (`GESTOR` | `OPERADOR`). Os outros serviços validam com o mesmo `JWT_SECRET`, sem chamar este serviço. O `motoristas-service` e o `manifesto-service` **ainda não exigem token**: para protegê-los, é preciso adicionar `spring-boot-starter-security` e um filtro equivalente ao `JwtAuthFilter` deste projeto.

## Limitações conhecidas

- O token não é revogável: um usuário desativado perde acesso a `/api/auth/me` na hora, mas um token já emitido continua válido nos outros serviços até expirar. Mantenha `JWT_EXPIRATION_MINUTES` curto se isso importar.
- Não há refresh token nem fluxo de "esqueci a senha": hoje o gestor cria o usuário e, se necessário, desativa e recria a conta.
- O bloqueio por tentativas é por conta, não por IP. Para limitar abuso por IP, use o gateway ou um proxy reverso.
- O segredo compartilhado (HS256) exige que todos os serviços o conheçam; migrar para RS256 permitiria distribuir só a chave pública.
