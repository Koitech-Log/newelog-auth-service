# newelog-auth-service

> Projeto Integrador 3º Semestre · DSM · FATEC São José dos Campos · Equipe **Koitech-Log** · Cliente **NEWE Logística Integrada**
>
> [Repositório principal](https://github.com/Koitech-Log/Koitech-Log-3DSM-Newelog) · [Documentação do projeto](https://github.com/Koitech-Log/Documentos) · [motoristas-service](https://github.com/Koitech-Log/newelog-motoristas-service) · [manifesto-service](https://github.com/Koitech-Log/newelog-manifesto-service) · [frontend](https://github.com/Koitech-Log/newelog-frota-web)

Autenticação e gestão de usuários do NEWELOG. Emite o JWT (HS256) usado pelo front-end e validado pelos demais serviços.

## O que este serviço entrega

| Sprint | Funcionalidade | Situação |
|---|---|---|
| 2 | Login com e-mail e senha (US04) | ✅ `POST /api/auth/login` |
| 2 | Controle de acesso por perfil Gestor / Operador (US05) | ✅ perfil no token, validado por todos os serviços |
| 3 | Cadastro de usuário pelo gestor com nome, e-mail, perfil e senha inicial (US10.1 / US10.2) | 🟨 rota `POST /api/usuarios` pronta; troca obrigatória da senha no primeiro acesso e tela no front ainda pendentes |
| 3 | Listar e buscar usuários (US11.1) | 🟨 `GET /api/usuarios` lista; busca/filtro ainda pendente |
| 3 | Alterar perfil de um usuário (US11.2) | ❌ não iniciado |
| 3 | Histórico das alterações de perfil (US11.3) | ❌ não iniciado |

**Stack:** Java 21, Spring Boot 3.5.16, Spring Security, JPA, Flyway, PostgreSQL 16, jjwt 0.12.

## Sumário

- [Rodando](#rodando)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [API](#api)
- [Validando o token nos outros serviços](#validando-o-token-nos-outros-serviços)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Limitações conhecidas](#limitações-conhecidas)

## Rodando

Pré-requisito: Docker com Docker Compose.

```bash
cp .env.example .env   # preencha JWT_SECRET, ADMIN_EMAIL e ADMIN_PASSWORD (veja abaixo)
docker compose up --build
```

- `JWT_SECRET`: mínimo de 32 caracteres. Gere um com `openssl rand -base64 48`.
- `ADMIN_EMAIL` / `ADMIN_PASSWORD`: credenciais do gestor inicial. A senha precisa ter **no mínimo 8 caracteres**.

O serviço sobe em `http://localhost:8082` e o Postgres em `localhost:5433` (5433 no host para não colidir com o `motoristas-db`, que usa 5432).

No primeiro start, com a tabela de usuários vazia, o gestor inicial é criado a partir de `ADMIN_EMAIL`/`ADMIN_PASSWORD`. Se esses valores estiverem ausentes ou a senha tiver menos de 8 caracteres, o gestor **não** é criado e aparece apenas um aviso no log, e ninguém consegue entrar até que ele seja criado. Troque a senha do gestor depois do primeiro login (`POST /api/auth/alterar-senha`).

Para checar se o serviço está de pé: `GET http://localhost:8082/actuator/health`.

### Testes

Rodam com H2 em memória (modo PostgreSQL), sem precisar de banco:

```bash
./mvnw clean verify
```

O mesmo comando roda no CI (GitHub Actions) a cada push ou pull request na `main`.

## Variáveis de ambiente

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | sim | – | Conexão PostgreSQL (o `docker-compose.yml` já preenche `DB_URL`) |
| `JWT_SECRET` | sim | – | Segredo HS256 (mín. 32 caracteres) |
| `JWT_EXPIRATION_MINUTES` | não | 480 | Validade do token |
| `FRONTEND_URL` | não | `http://localhost:5173` | Origem liberada no CORS |
| `ADMIN_NOME` | não | `Administrador` | Nome do gestor inicial |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | não* | – | Gestor inicial (senha com mín. 8 caracteres) |
| `PORT` | não | 8082 | Porta HTTP |
| `DB_POOL_MAX_SIZE` | não | 10 | Máximo de conexões no pool |
| `DB_POOL_MIN_IDLE` | não | 2 | Mínimo de conexões ociosas no pool |

\* Opcionais para o serviço subir, mas sem elas e sem usuários cadastrados ninguém consegue fazer login.

## API

Erros seguem o padrão dos outros serviços: `{ "timestamp": "...", "mensagem": "..." }`.

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/api/auth/login` | público | `{ email, senha }` → `{ token, tipo, expiraEm, nome, perfil }` |
| GET | `/api/auth/me` | autenticado | Dados do usuário do token (consulta o banco) |
| POST | `/api/auth/alterar-senha` | autenticado | `{ senhaAtual, novaSenha }` → 204 |
| GET | `/api/usuarios` | gestor | Lista usuários, ordenados por nome |
| POST | `/api/usuarios` | gestor | `{ nome, email, senha, perfil }` → 201 |
| PATCH | `/api/usuarios/{id}/ativo` | gestor | `{ ativo }` |
| GET | `/actuator/health`, `/actuator/info` | público | Saúde e informações do serviço |

As rotas autenticadas exigem o header `Authorization: Bearer <token>`.

### Exemplo

```bash
# login
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@exemplo.com","senha":"sua-senha"}'

# usar o token
curl http://localhost:8082/api/auth/me -H "Authorization: Bearer <token>"
```

### Regras e validações

- `perfil` é `"gestor"` ou `"operador"` (minúsculas na resposta; a entrada aceita qualquer caixa), igual ao tipo `Perfil` do front.
- Criação de usuário: `nome` até 150 caracteres, `email` válido até 160 caracteres, `senha` entre 8 e 72 caracteres. E-mail repetido retorna 409.
- Alterar senha: a senha atual precisa estar correta e a nova deve ser diferente da atual (400 nos dois casos).
- Um gestor não pode desativar o próprio usuário (400).

### Login: códigos de resposta

| Código | Situação |
|---|---|
| 401 | E-mail ou senha incorretos |
| 403 | Conta desativada (só é informado depois que a senha está correta) |
| 429 | Conta bloqueada por excesso de tentativas |

Após 5 senhas erradas seguidas a conta fica bloqueada por 15 minutos.

## Validando o token nos outros serviços

O token é um JWT HS256 com `iss = newelog-auth-service`, `sub = id do usuário` e as claims `email`, `nome` e `perfil` (`GESTOR` | `OPERADOR`, em maiúsculas no token). Os outros serviços validam com o mesmo `JWT_SECRET`, sem chamar este serviço.

O `motoristas-service` e o `manifesto-service` já exigem o token em todas as rotas (exceto `/actuator/health` e `/actuator/info`), validando assinatura, `iss` e `perfil` com o mesmo `JWT_SECRET`.

## Estrutura do projeto

```
src/main/java/br/com/newelog/auth/
├── config/        SecurityConfig (rotas, CORS, BCrypt)
├── controller/    AuthController, UsuarioController, ApiExceptionHandler
├── dto/           Requests e responses
├── model/         Usuario, Perfil
├── repository/    UsuarioRepository
├── security/      JwtService, JwtAuthFilter
└── service/       AuthService, UsuarioService, AdminInicialRunner
src/main/resources/db/migration/   Migrations do Flyway
```

## Limitações conhecidas

- O token não é revogável: um usuário desativado perde acesso a `/api/auth/me` na hora, mas um token já emitido continua válido nos outros serviços até expirar. Mantenha `JWT_EXPIRATION_MINUTES` curto se isso importar.
- Não há refresh token nem fluxo de "esqueci a senha": hoje o gestor cria o usuário e, se necessário, desativa e recria a conta.
- O bloqueio por tentativas é por conta, não por IP. Para limitar abuso por IP, use o gateway ou um proxy reverso.
- O segredo compartilhado (HS256) exige que todos os serviços o conheçam; migrar para RS256 permitiria distribuir só a chave pública.