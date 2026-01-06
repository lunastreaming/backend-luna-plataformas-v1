# 1. Cambiar a Java 21
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Copiar archivos de configuración de maven primero (ayuda a la caché de Docker)
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline

# Copiar el código fuente y compilar
COPY src ./src
RUN ./mvnw clean package -DskipTests

EXPOSE 8080

# Comando para ejecutar la app
CMD ["java", "-jar", "target/lunastreaming-0.0.1-SNAPSHOT.jar"]