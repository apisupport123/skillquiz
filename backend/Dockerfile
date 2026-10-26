# Render has no native Java runtime, so we deploy with Docker.
# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- run stage (small image) ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/skillquiz.jar app.jar
# Render free = 512 MB RAM. These flags keep Spring Boot comfortably inside it and start faster.
ENV JAVA_OPTS="-Xmx300m -Xss512k -XX:MaxMetaspaceSize=140m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
