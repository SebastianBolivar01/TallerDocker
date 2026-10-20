# ========================================================
# Etapa 1: Builder (Compilacion y resolucion de dependencias)
# ========================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Argumento de compilacion requerido
ARG STUDENT_ID

# Aislar la descarga del pom.xml y resolver dependencias en cache
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Transferir codigo fuente y empaquetar omitiendo pruebas
COPY src ./src
RUN mvn clean package -DskipTests

# ========================================================
# Etapa 2: Runtime (Entorno de ejecucion seguro y ligero)
# ========================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Argumento para transferir el identificador a la etapa de runtime
ARG STUDENT_ID

# Crear usuario del sistema sin privilegios (UID 10001) asignado a sensorgroup
RUN addgroup -g 10001 -S sensorgroup && \
    adduser -u 10001 -S sensor_user -G sensorgroup

# Copiar unicamente el archivo .jar resultante y cambiar la titularidad de /app
COPY --from=builder --chown=sensor_user:sensorgroup /build/target/*.jar /app/app.jar
RUN chown -R sensor_user:sensorgroup /app

# Variables de entorno ergonomicas para la JVM en contenedor
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

# Configurar variable de entorno con el ID del estudiante
ENV OPERATOR_ID=${STUDENT_ID}

# Exposicion del puerto de red
EXPOSE 8080

# Cambiar al usuario no privilegiado antes del ENTRYPOINT
USER sensor_user

# Comando de arranque del servicio
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
