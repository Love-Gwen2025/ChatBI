# ChatBI Web - 多阶段构建
# 阶段1: Node 构建
FROM node:22-alpine AS builder

WORKDIR /build
COPY package.json ./
RUN npm install

COPY . .
RUN npm run build

# 阶段2: Nginx 静态服务
FROM nginx:alpine

COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
