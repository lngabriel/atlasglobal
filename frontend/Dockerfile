FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm test && npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY trusted-proxies.conf /etc/nginx/atlas/trusted-proxies.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 8080
