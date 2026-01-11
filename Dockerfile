# Build Stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build the Fat JAR
# 跳过测试以加快构建速度 / Skip tests to speed up build
RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/polymarket-copy-trader-1.0-SNAPSHOT.jar bot.jar
# COPY .env.example .env

# Set default env vars (can be overridden by cloud provider)
# 设置默认环境变量（可由云提供商覆盖）
ENV TRADE_MODE=SIMULATION
ENV MAX_DAILY_TRADES=50
ENV MIN_WIN_RATE=0.60

# Run the bot
ENTRYPOINT ["java", "-jar", "bot.jar"]
