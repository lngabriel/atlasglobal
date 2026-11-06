# Validação da Fase 1

Verificações realizadas em 6 de novembro de 2026.

## Backend

Ambiente: Eclipse Temurin JDK 21.0.13, Maven 3.9.12 e Spring Boot 3.5.16.

Comando executado com `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` direcionados a um banco temporário dedicado:

```bash
mvn -B -f backend/pom.xml -Pintegration verify
```

Resultado: **BUILD SUCCESS**, cinco testes aprovados, sem falhas, erros ou testes ignorados.

- CORS rejeita origens com curinga.
- Preflight permite a origem configurada e GET; rejeita origem desconhecida e POST.
- Exceção inesperada retorna ProblemDetail genérico.
- Conexão PostgreSQL funciona, Flyway V1 é aplicada e nenhuma tabela de domínio existe.
- Readiness retorna UP sem detalhes; `/actuator/env` retorna 404 e health não existe na porta principal.

O PostgreSQL instalado no ambiente é 18.6. A migração e os testes passaram nessa versão, mas o Flyway gerenciado pelo Spring Boot emitiu aviso de suporte testado até PostgreSQL 17. O Compose utiliza PostgreSQL 17; sua execução continua pendente até haver Docker Engine disponível. Não foi alterada a versão do Compose para contornar o aviso do ambiente local.

## Frontend

Ambiente: Node.js 22.23.4 e npm 10.9.9.

- `npm run typecheck`: aprovado.
- `npm run build`: aprovado, com Vite 7.3.7.
- `npm audit --omit=dev --audit-level=high`: nenhuma vulnerabilidade encontrada nas dependências de runtime.
- Verificação no Chrome headless: início renderizado, navegação para sobre, gravação e persistência do tema após recarregar, ausência de rolagem horizontal em viewport de 390 px e fallback para rota desconhecida aprovados.
- Capturas de desktop e celular inspecionadas localmente.

As verificações de navegador foram realizadas com script operacional temporário, sem adicionar dependências ou uma suíte de testes de interface ao projeto.

## Infraestrutura

- `docker compose config --quiet` com senha não vazia: aprovado pelo binário oficial Compose v5.7.0 em modo standalone.
- Mesma validação com senha vazia: rejeitada como esperado.
- Porta 8081 não publicada; portas públicas locais vinculadas a 127.0.0.1.
- `.env` e ferramentas locais ignorados pelo Git.

**Limitação:** Docker Engine não está instalado. Não foram executados `docker compose up`, builds de imagens ou verificações de Nginx dentro do contêiner. Execute o roteiro do README em um ambiente com Docker para concluir essa validação.

## Escopo preservado

Nenhuma integração com APIs externas, entidade de domínio, conta, autenticação ou operação administrativa pública foi implementada. Nenhum push ou deploy foi executado. A Fase 2 depende de aprovação explícita.
