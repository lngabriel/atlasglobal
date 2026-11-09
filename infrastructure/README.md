# Infraestrutura local

Os serviços são definidos no `docker-compose.yml` da raiz. Os contextos de build são `backend/` e `frontend/`; cada um possui Dockerfile e `.dockerignore`.

- PostgreSQL 17 Alpine: volume persistente e health check.
- Backend: build Maven/JDK 21 e runtime JRE 21 sem root.
- Frontend: build Node 22 e runtime Nginx sem root, com fallback de rotas SPA.
- A porta Actuator não é publicada nem encaminhada pelo proxy.
- Todas as portas publicadas estão vinculadas a 127.0.0.1.

Consulte o README principal para variáveis e comandos. A configuração local não inclui hospedagem pública nem TLS.
