# ChatBI Server - 多阶段构建
# 阶段1: Maven 构建
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
# 先下载依赖（利用 Docker 缓存）
RUN mvn dependency:go-offline -B

COPY src/ ./src/
RUN mvn package -DskipTests -B

# 阶段2: 运行时
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=builder /build/target/chatbi-server-*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
