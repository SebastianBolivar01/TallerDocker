# Taller Docker: Contenerización, Seguridad de Runtime y Estrés de cgroups

Repositorio oficial para el desarrollo del Taller de Docker de la asignatura **Programación Orientada a Objetos (POO)** en la **Universidad Cooperativa de Colombia (UCC)**.

---

## 👥 Integrantes del Proyecto
* **Sebastián Bolívar** &bull; Código ID: `907211`
* **Julián Mera** &bull; Código ID: `900264`

---

## 📌 Contexto del Proyecto: `eco-air-backend`
El microservicio **`eco-air-backend`** procesa telemetría y alertas ambientales críticas (CO₂, PM2.5, temperatura y humedad) en estaciones meteorológicas. En entornos operacionales de borde con recursos acotados, el backend debe operar bajo rigurosas cuotas de hardware, sin privilegios de superusuario (`root`) y protegido contra agotamiento de memoria del host.

---

## 🏛️ Arquitectura de Seguridad Implementada

### 1. Construcción Multietapa (Multi-Stage Build)
* **Etapa 1 (Builder - `maven:3.9.6-eclipse-temurin-17-alpine`)**: 
  - Aislamiento del descriptor `pom.xml` y descarga anticipada mediante `mvn dependency:go-offline -B` para aprovechar el caché inmutable de capas de Docker.
  - Inyección del identificador conjunto de los estudiantes en tiempo de compilación: `ARG STUDENT_ID="907211_900264"`.
  - Empaquetado del binario ejecutable `.jar` omitiendo pruebas (`mvn clean package -DskipTests`).
* **Etapa 2 (Runtime - `eclipse-temurin:17-jre-alpine`)**: 
  - Entorno de producción ultraligero y seguro (sin Maven, sin herramientas JDK ni código fuente Java).
  - Usuario del sistema no privilegiado: **`sensor_user`** con **`UID 10001`** asignado al grupo **`sensorgroup`** (**`GID 10001`**).
  - Titularidad restringida del directorio `/app` y del artefacto `/app/app.jar`.
  - Variables ergonómicas de la JVM para el reconocimiento de cgroups:
    ```bash
    ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"
    ```
  - Variable de entorno inyectada: `ENV OPERATOR_ID=${STUDENT_ID}`.
  - Conmutación obligatoria a `USER sensor_user` antes del `ENTRYPOINT`.

---

## 🚀 Guía de Ejecución Rápida

### Construcción de la Imagen
```bash
docker build --build-arg STUDENT_ID="907211_900264" -t eco-air-backend:1.0.0 ./eco-air-backend
```

### Ejecución con Cuota Calibrada de Memoria (256 MB)
```bash
docker run -d --name eco_air_active -p 8080:8080 -m 256m eco-air-backend:1.0.0
```

### Pruebas de Auditoría y Diagnóstico Forense
```bash
# 1. Desacoplamiento de capas
docker history --human --format "table {{.Size}}\t{{.CreatedBy}}" eco-air-backend:1.0.0

# 2. Aislamiento de procesos (verificar ejecución como sensor_user / UID 10001)
docker top eco_air_active -o pid,user,comm

# 3. Inmutabilidad de runtime (intento de escritura en /etc)
docker exec eco_air_active touch /etc/crontab_fake

# 4. Auditoría de artefactos de desarrollo (verificar ausencia de maven y .java)
docker exec eco_air_active which mvn
docker exec eco_air_active sh -c 'find / -name "*.java" 2>/dev/null'

# 5. Inducción de colapso por estrés de memoria (OOM Killer)
docker run -d --name eco_air_crash -m 48m eco-air-backend:1.0.0
docker inspect eco_air_crash --format='Status: {{.State.Status}} | ExitCode: {{.State.ExitCode}} | OOMKilled: {{.State.OOMKilled}}'

# 6. Profiling en caliente
docker stats --no-stream eco_air_active
```

---

## 📂 Estructura del Repositorio
```
.
├── eco-air-backend/                # Código fuente del microservicio Spring Boot 3 + Hibernate
│   ├── pom.xml
│   ├── Dockerfile
│   ├── .dockerignore
│   └── src/
├── taller-docker-semana13/         # Paquete oficial requerido para la entrega
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── audit_session.log           # Registro crudo de terminal cronometrado
│   └── FORENSIC_REPORT.md          # Reporte forense con firmas SHA-256 y diagnósticos
├── taller_docker_semana13.zip      # Archivo comprimido listo para entrega académica
├── JUSTIFICACION_TALLER_DOCKER.html # Documento completo imprimible a PDF
└── README.md
```
