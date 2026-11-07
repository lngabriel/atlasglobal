# Fundação — decisão arquitetural

## Escopo

Monólito modular Java 21 / Spring Boot 3.5.16 e frontend separado React 19 / TypeScript / Vite. PostgreSQL com Flyway. Nenhuma conta, autenticação, Spring Security, entidade de usuário ou integração externa.

## Limites e dependências

- `AtlasGlobalApplication` inicializa o backend.
- `shared` contém infraestrutura transversal: CORS e tratamento de exceções.
- `country`, `indicator`, `observation` e `ingestion` têm somente documentação de pacote, sem entidades, serviços ou contratos fictícios.
- As funcionalidades serão organizadas por domínio, com controllers, DTOs, serviços e repositórios próximos de suas regras quando forem implementados.
- O frontend organiza navegação em `app`, páginas em `pages`, componentes em `components`, clientes em `services` e comportamento reutilizável em `hooks`.
- TanStack Query está configurado sem consultas fictícias. Nenhum dado externo é requisitado pela aplicação.

Apenas o tema é salvo em localStorage. A fonte tipográfica utiliza a pilha do sistema: não há dependência de serviços externos de fontes.

## Banco e migrações

Flyway é responsável pelo esquema. `ddl-auto=validate` impede geração de tabelas por Hibernate. A migração V1 executa uma instrução SQL sem criar entidades: demonstra conectividade e inicializa o histórico. O modelo relacional será introduzido em migrações posteriores, após sua definição.

O volume do Compose preserva o PostgreSQL entre reinicializações. Credenciais são obrigatórias por variável de ambiente. PostgreSQL 17 é a versão de referência do Compose.

## Superfície pública e operacional

O backend não tem controllers de negócio nesta fase. Não existe rota de importação ou administração.

CORS permite apenas origens explícitas e métodos GET, HEAD e OPTIONS em `/api/**`, sem credenciais. CORS é uma política do navegador, não substitui a restrição de operações no servidor.

Actuator funciona em porta separada, vinculada a 127.0.0.1 por padrão. Somente health está exposto, sem detalhes. Readiness inclui o banco; liveness não depende dele. O Compose não publica a porta de gerenciamento e o Nginx não a encaminha.

Exceções MVC usam ProblemDetail; falhas inesperadas retornam mensagem genérica e são registradas internamente. Logs usam JSON ECS. O envio multipart está desabilitado.

## Interface

Início e página sobre o projeto são as únicas rotas funcionais além do fallback 404. Sem pesquisa, indicadores, comparações ou gráficos antecipados. A página inicial abre diretamente na área de indicadores, com estado vazio explícito até a importação de dados reais, sem bloco promocional ou ilustração decorativa. Modo claro/escuro, layout responsivo, navegação por teclado e link para pular ao conteúdo compõem a fundação visual.

shadcn/ui é configurado com Tailwind 4, variáveis CSS, aliases e componente Button local baseado em Radix Slot. Não existe serviço shadcn em runtime.

## Execução

No Compose, Nginx serve a aplicação estática e encaminha `/api` ao backend. Backend e frontend usam builds em múltiplas etapas e processos sem privilégios de root no runtime. PostgreSQL precisa estar saudável antes de iniciar o backend.

No desenvolvimento, Vite encaminha `/api` ao backend local. Segredos não são transmitidos ao frontend. Arquivos `.env`, resultados de build e ferramentas locais são ignorados pelo Git.

## Adiado deliberadamente

Entidades e tabelas de domínio, integração World Bank, endpoints de consulta, OpenAPI, cache de dados, jobs, comparador, rankings, mapa, CSV, rate limiting de produção e CI/CD pertencem às próximas etapas. Nenhuma dessas funcionalidades é simulada nesta fase.
