FROM node:22-bookworm-slim AS frontend

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
ENV BASE_PATH=/game/
RUN npm run build:backend

FROM nginx:1.28-alpine

COPY backend/docker/nginx/production-container.conf /etc/nginx/conf.d/default.conf
COPY backend/public/ /var/www/html/backend/public/
COPY --from=frontend /app/dist/ /var/www/html/backend/public/game/

EXPOSE 8080
