# Atlas Global

Plataforma pública e gratuita para explorar dados internacionais. **Fase 1 — Fundação**: infraestrutura, backend e interface inicial. Ainda não existem integrações externas, indicadores, entidades de domínio ou endpoints de negócio. Não há contas nem autenticação.

## Requisitos

- Docker Engine com Docker Compose v2 para executar os contêineres.
- Para desenvolvimento fora dos contêineres: JDK 21, Maven 3.9+, Node.js 22.12+ da linha 22 e npm.
- Portas locais padrão: 3000 (interface Docker), 5173 (Vite), 8080 (backend), 8081 (health local), 5432 (PostgreSQL).

## Executar tudo com Docker Compose

```bash
cp .env.example .env
```

Edite `.env` e preencha `DB_PASSWORD` com uma senha local. O Compose recusa senha vazia. Não versione esse arquivo.

```bash
docker compose config --quiet
docker compose up --build -d
docker compose ps
docker compose logs backend
```

Acesse **http://localhost:3000**. A interface tem início, página sobre o projeto e alternância de tema. A aplicação não apresenta números ou consultas simuladas.

A porta de gerenciamento 8081 não é publicada pelo Compose. Para verificar o backend e o banco a partir do próprio contêiner:

```bash
docker compose exec backend wget -qO- http://127.0.0.1:8081/actuator/health/readiness
```

A resposta esperada é `{"status":"UP"}`. O grupo readiness inclui a conexão com o banco. A inicialização do backend falha se o Flyway não conseguir aplicar/validar as migrações.

Verifique a migração diretamente no banco:

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, description, success FROM flyway_schema_history;"'
```

Deve existir a versão `1` com `success = true`. A migração inicial não cria tabelas de domínio. O Flyway cria sua própria tabela de histórico.

```bash
docker compose down
```

Esse comando preserva o volume. Não use `down -v` se quiser manter o banco. Alterar a senha em `.env` não altera a senha de um banco já inicializado: nesse caso, faça a alteração operacional no PostgreSQL.

## Desenvolvimento local

Prepare `.env` conforme a seção anterior e suba somente o banco:

```bash
docker compose up -d postgres
```

Em um terminal Bash na raiz do projeto, exporte a configuração local. O arquivo é carregado pelo shell; utilize apenas o `.env` local que você mesmo preparou.

```bash
set -a
source .env
set +a
export DB_URL="jdbc:postgresql://127.0.0.1:${DB_PORT:-5432}/${DB_NAME:-atlas_global}"
mvn -f backend/pom.xml spring-boot:run
```

Confirme que `java -version` e `mvn -version` apontam para Java 21. Se necessário, ajuste `JAVA_HOME`.

Em outro terminal:

```bash
cd frontend
npm ci
npm run dev
```

Acesse **http://localhost:5173**. O Vite encaminha `/api` para o backend local; nesta fase não há endpoints de negócio. O frontend não precisa de senha ou variáveis sensíveis.

Verificação operacional local:

```bash
curl --fail http://127.0.0.1:8081/actuator/health/readiness
```

`http://localhost:8080/actuator/health` deve responder **404**: o gerenciamento usa outra porta. No gerenciamento, somente `health` está exposto; `env`, `beans` e demais endpoints não são publicados.

## Compilação e testes

Testes unitários e compilação do backend, sem banco:

```bash
mvn -f backend/pom.xml verify
```

Testes de integração contra **um banco de teste dedicado e vazio**, com PostgreSQL em execução:

```bash
export DB_URL=jdbc:postgresql://127.0.0.1:5432/atlas_test
export DB_USERNAME=atlas
export DB_PASSWORD='sua-senha-local'
mvn -f backend/pom.xml -Pintegration verify
```

Crie `atlas_test` previamente com o proprietário correspondente. O perfil aplica a migração V1 nesse banco e verifica a conexão, a ausência de tabelas de domínio e o isolamento do Actuator. Ele não apaga tabelas nem limpa o banco. Reutilizar um banco contendo outras tabelas fará o teste de esquema falhar intencionalmente.

Frontend:

```bash
cd frontend
npm ci
npm run typecheck
npm run build
```

`package-lock.json` fixa as versões resolvidas; utilize `npm ci` para reproduzir a instalação. Os componentes shadcn/ui ficam no código-fonte (`components/ui`), com aliases e tema configurados em `components.json`.

## Configuração

| Variável | Uso |
| --- | --- |
| `DB_PASSWORD` | Obrigatória no backend e no Compose; nunca versionada. |
| `DB_USERNAME` | Usuário do banco; padrão `atlas`. |
| `DB_NAME`, `DB_PORT` | Banco e porta publicados pelo Compose. |
| `DB_URL` | URL JDBC do backend local; no Compose utiliza o serviço `postgres`. |
| `BACKEND_PORT`, `FRONTEND_PORT` | Portas publicadas pelo Compose. |
| `SERVER_PORT` | Porta HTTP do processo Java fora do Compose; padrão 8080. |
| `CORS_ALLOWED_ORIGINS` | Lista de origens exatas separadas por vírgula; sem curingas. |
| `MANAGEMENT_PORT`, `MANAGEMENT_ADDRESS` | Gerenciamento operacional; padrão 8081 e 127.0.0.1. Mantenha restrito. |

O Compose publica portas apenas em `127.0.0.1`. Esta é uma configuração de desenvolvimento local, não uma implantação de produção. O usuário PostgreSQL criado pela imagem é operacional; a separação de papéis de banco para produção será definida na etapa de implantação.

## Estrutura

```text
backend/         Spring Boot, configurações, migração e testes
frontend/        React, Vite, Tailwind e componentes shadcn/ui
infrastructure/  Orientações da infraestrutura local
docs/           Decisões de arquitetura e validação
```

Consulte [a arquitetura inicial](docs/architecture/foundation.md) e [as verificações da fase](docs/validation.md). Nenhum push ou deploy faz parte desta entrega.
