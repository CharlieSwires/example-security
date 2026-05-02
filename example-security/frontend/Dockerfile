# ---- Build stage ----
FROM node:20-alpine AS build

WORKDIR /app

ARG VITE_API_BASE=http://localhost:8080/ExampleSecurity
ENV VITE_API_BASE=$VITE_API_BASE

COPY package.json ./
RUN npm install

COPY index.html ./
COPY src ./src

RUN npm run build

# ---- Runtime stage ----
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
