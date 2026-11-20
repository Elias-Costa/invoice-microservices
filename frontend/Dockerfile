FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:stable-alpine
COPY infra/nginx/default.conf /etc/nginx/nginx.conf
COPY --from=build /app/dist/invoice-frontend/browser/ /usr/share/nginx/html/
USER nginx
EXPOSE 80
ENTRYPOINT ["nginx", "-g", "daemon off;"]
CMD []
