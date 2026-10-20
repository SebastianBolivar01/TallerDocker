# INFORME FORENSE Y AUDITORÍA DE SEGURIDAD EN RUNTIME
## Contenerización, Seguridad de Runtime y Calibración de cgroups

* **Proyecto:** `eco-air-backend`
* **Modalidad:** Parejas (Individual o Parejas según directrices)
* **Integrantes:** 
  - Sebastián Bolívar &bull; ID: `907211`
  - Julián Mera &bull; ID: `900264`
* **Identificador Conjunto (OPERATOR_ID):** `907211_900264`
* **Institución:** Universidad Cooperativa de Colombia (UCC)
* **Fecha de Auditoría:** 20 de Octubre de 2026
* **Entorno de Ejecución:** Docker Engine 28.5.1 sobre WSL2 Linux Kernel 5.15 x86_64

---

## 1. Tabla de Identidad Forense

A continuación se certifican los identificadores únicos e inmutables generados por el daemon local de Docker durante la ejecución de este taller:

| Parámetro Auditado | Comando de Extracción | Salida Exacta / Firma Criptográfica |
| :--- | :--- | :--- |
| **Hash de la Imagen (SHA-256)** | `docker inspect --format='{{.Id}}' eco-air-backend:1.0.0` | `sha256:6e3903344beb59ebcdec840b2419a84847b5936f7c0c5937f865fa13d9bcac8b` |
| **Variable Inyectada** | `docker exec eco_air_active env \| grep OPERATOR_ID` | `OPERATOR_ID=907211_900264` |
| **Consumo Calibrado Final** | `docker stats --no-stream eco_air_active` | `8b4fbcc8f290 \| eco_air_active \| CPU: 0.12% \| MEM: 212.3MiB / 256MiB (82.93%) \| NET: 872B/126B \| PIDS: 31` |

### Salida cruda de la Tabla de Identidad:
```bash
$ docker inspect --format='{{.Id}}' eco-air-backend:1.0.0
sha256:6e3903344beb59ebcdec840b2419a84847b5936f7c0c5937f865fa13d9bcac8b

$ docker exec eco_air_active sh -c "env | grep OPERATOR_ID"
OPERATOR_ID=907211_900264

$ docker stats --no-stream eco_air_active
CONTAINER ID   NAME             CPU %     MEM USAGE / LIMIT   MEM %     NET I/O       BLOCK I/O     PIDS
8b4fbcc8f290   eco_air_active   0.12%     212.3MiB / 256MiB   82.93%    872B / 126B   46.4MB / 0B   31
```

---

## 2. Reto 1: Construcción Multietapa con Huella Estricta

### 2.1 Justificación Arquitectónica
Para cumplir con los principios de *Mínimo Privilegio* y *Reducción de Superficie de Ataque*, se implementó una estrategia **Multi-Stage Build**:
1. **Etapa 1 (`builder`) - `maven:3.9.6-eclipse-temurin-17-alpine`:**
   - Se aisló la transferencia del archivo `pom.xml` antes de copiar el código fuente `src/`.
   - Se ejecutó `mvn dependency:go-offline -B` para predescargar y congelar todas las dependencias en la capa de caché de Docker. Si el desarrollador modifica únicamente el código fuente, Docker reutiliza esta capa sin re-descargar dependencias por red.
   - Se inyectó el parámetro de compilación conjunto de la pareja: `ARG STUDENT_ID="907211_900264"`.
   - Se empaquetó el artefacto omitiendo las pruebas unitarias (`mvn clean package -DskipTests`).
2. **Etapa 2 (`runtime`) - `eclipse-temurin:17-jre-alpine`:**
   - Imagen minimalista de Alpine Linux con JRE (sin Maven, sin herramientas JDK de desarrollo como `javac` o compiladores).
   - Creación de grupo y usuario del sistema no privilegiado `sensor_user` con UID y GID `10001` (`sensorgroup`).
   - Transferencia exclusiva del artefacto `.jar` (`/build/target/*.jar -> /app/app.jar`) con titularidad delegada a `sensor_user:sensorgroup`.
   - Configuración ergonómica de la JVM mediante `JAVA_TOOL_OPTIONS`:
     `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom`
   - Configuración de la variable de entorno `ENV OPERATOR_ID=${STUDENT_ID}`.
   - Activación de `USER sensor_user` con anterioridad al `ENTRYPOINT`.

### 2.2 Dockerfile Implementado
```dockerfile
# ========================================================
# Etapa 1: Builder (Compilación y resolución de dependencias)
# ========================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Argumento de compilación requerido
ARG STUDENT_ID

# Aislar la descarga del pom.xml y resolver dependencias en caché
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Transferir código fuente y empaquetar omitiendo pruebas
COPY src ./src
RUN mvn clean package -DskipTests

# ========================================================
# Etapa 2: Runtime (Entorno de ejecución seguro y ligero)
# ========================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Argumento para transferir el identificador a la etapa de runtime
ARG STUDENT_ID

# Crear usuario del sistema sin privilegios (UID 10001) asignado a sensorgroup
RUN addgroup -g 10001 -S sensorgroup && \
    adduser -u 10001 -S sensor_user -G sensorgroup

# Copiar únicamente el archivo .jar resultante y cambiar la titularidad de /app
COPY --from=builder --chown=sensor_user:sensorgroup /build/target/*.jar /app/app.jar
RUN chown -R sensor_user:sensorgroup /app

# Variables de entorno ergonómicas para la JVM en contenedor
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

# Configurar variable de entorno con el ID del estudiante
ENV OPERATOR_ID=${STUDENT_ID}

# Exposición del puerto de red
EXPOSE 8080

# Cambiar al usuario no privilegiado antes del ENTRYPOINT
USER sensor_user

# Comando de arranque del servicio
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### 2.3 Archivo `.dockerignore`
```dockerignore
target/
.git
.gitignore
.idea
*.iml
.vscode
*.log
Dockerfile*
.dockerignore
```

---

## 3. Reto 2: Auditoría Forense y Comprobación de Vulnerabilidades

### 3.1 Verificación del Desacoplamiento de Capas
**Comando:**
```bash
docker history --human --format "table {{.Size}}\t{{.CreatedBy}}" eco-air-backend:1.0.0
```
**Salida de Terminal:**
```
SIZE      CREATED BY
0B        ENTRYPOINT ["java" "-jar" "/app/app.jar"]
0B        USER sensor_user
0B        EXPOSE [8080/tcp]
0B        ENV OPERATOR_ID=907211_900264
0B        ENV JAVA_TOOL_OPTIONS=-XX:+UseContainerSuppo…
8.19kB    RUN |1 STUDENT_ID=907211_900264 /bin/sh -c c…
49.5MB    COPY --chown=sensor_user:sensorgroup /build/…
41kB      RUN |1 STUDENT_ID=907211_900264 /bin/sh -c a…
0B        ARG STUDENT_ID=907211_900264
8.19kB    WORKDIR /app
0B        ENTRYPOINT ["/__cacert_entrypoint.sh"]
12.3kB    COPY --chmod=755 entrypoint.sh /__cacert_ent…
12.3kB    RUN /bin/sh -c set -eux;     echo "Verifying…
141MB     RUN /bin/sh -c set -eux;     ARCH="$(apk --p…
0B        ENV JAVA_VERSION=jdk-17.0.20.1+1
44MB      RUN /bin/sh -c set -eux;     apk add --no-ca…
0B        ENV LANG=en_US.UTF-8 LANGUAGE=en_US:en LC_AL…
0B        ENV PATH=/opt/java/openjdk/bin:/usr/local/sb…
0B        ENV JAVA_HOME=/opt/java/openjdk
0B        CMD ["/bin/sh"]
9.08MB    ADD alpine-minirootfs-3.24.2-x86_64.tar.gz /…
```
**Análisis Forense:**
- La capa del artefacto compilado (`COPY --chown=sensor_user:sensorgroup`) ocupa únicamente **49.5 MB**.
- Las capas de Maven y del repositorio local de compilación (~800 MB de plugins y caché de compilador) han sido descartadas en su totalidad, no existiendo rastro de ellas en la imagen final.
- La imagen final tiene un tamaño total aproximado de solo **243 MB**, compuesta exclusivamente por Alpine OS (9.08 MB), soporte C/SSL (44 MB), el runtime JRE Temurin (141 MB) y la aplicación (49.5 MB).

---

### 3.2 Inspección del Proceso del Host (Aislamiento de Privilegios)
**Comando de Inicio:**
```bash
docker run -d --name eco_air_active -p 8080:8080 -m 256m eco-air-backend:1.0.0
```
**Comando de Inspección de Procesos:**
```bash
docker top eco_air_active -o pid,user,comm
```
**Salida de Terminal:**
```
PID                 USER                COMMAND
8947                10001               java
```
**Evidencia y Análisis:**
El proceso `java` dentro del espacio de nombres (PID namespace) se encuentra asignado de manera estricta al identificador numérico de usuario **`10001`** correspondiente a `sensor_user`. En ningún momento se ejecuta bajo el UID `0` (`root`). Si un atacante lograra una vulnerabilidad de ejecución remota de código (RCE) a nivel de aplicación, sus capacidades estarían acotadas a este usuario sin privilegios, impidiendo el compromiso del kernel del host o la fuga del contenedor (*container breakout*).

---

### 3.3 Test de Penetración: Comprobación de Inmutabilidad de Runtime
**Comando de Intrusión simulada:**
```bash
docker exec eco_air_active touch /etc/crontab_fake
```
**Salida de Terminal:**
```
touch: cannot touch '/etc/crontab_fake': Permission denied
```
*(Código de salida: 1)*

**Evidencia y Análisis:**
El sistema de permisos DAC (*Discretionary Access Control*) de Linux previene que el usuario `sensor_user` (UID 10001) modifique rutas críticas del sistema de archivos como `/etc/`. Esto bloquea de raíz vectores de persistencia basados en tareas programadas (`cron`), manipulación de resolución de nombres (`/etc/resolv.conf`, `/etc/hosts`) o alteración de usuarios del sistema (`/etc/passwd`).

---

### 3.4 Auditoría de Artefactos de Desarrollo
**Comandos ejecutados:**
```bash
docker exec eco_air_active which mvn
docker exec eco_air_active sh -c 'find / -name "*.java" 2>/dev/null'
```
**Salida de Terminal:**
```
$ docker exec eco_air_active which mvn
# (Salida vacía, Código de salida: 1)

$ docker exec eco_air_active sh -c 'find / -name "*.java" 2>/dev/null'
# (Salida vacía, Código de salida: 1)

$ docker exec eco_air_active ls -la /app
total 48336
drwxr-xr-x 1 sensor_user sensorgroup     4096 Oct 20 23:43 .
drwxr-xr-x 1 root        root            4096 Oct 20 23:43 ..
-rw-r--r-- 1 sensor_user sensorgroup 49484880 Oct 20 23:43 app.jar
```
**Evidencia y Análisis:**
1. `which mvn`: Devuelve error (código 1). Maven no existe en el contenedor de producción. Esto imposibilita que un atacante recompile binarios maliciosos *in-situ*.
2. `find / -name "*.java"`: No localiza ningún archivo de código fuente Java. Solo existe el archivo binario precompilado `/app/app.jar`, protegiendo la propiedad intelectual del backend y previniendo inspección estática del código por atacantes locales.

---

## 4. Reto 3: Calibración de cgroups y Captura del Asesino OOM

### 4.1 Inducción del Colapso por Memoria (OOMKilled)
Se asignó una cuota de hardware intencionalmente insuficiente de **48 MB** (`-m 48m`) para forzar la saturación de los grupos de control de memoria (*cgroups v2*).

**Comando de Ejecución:**
```bash
docker run -d --name eco_air_crash -m 48m eco-air-backend:1.0.0
```
**Comando de Inspección de Estado (tras 15 segundos):**
```bash
docker inspect eco_air_crash --format='Status: {{.State.Status}} | ExitCode: {{.State.ExitCode}} | OOMKilled: {{.State.OOMKilled}}'
```
**Salida de Terminal:**
```
Status: exited | ExitCode: 137 | OOMKilled: true
```

**Explicación Técnica del Resultado:**
- **`OOMKilled: true`**: La JVM de Spring Boot con Hibernate requiere memoria para el *Metaspace* (clases cargadas), pila de hilos de ejecución (*thread stacks*), código JIT compilado y *Heap*. Al superar el límite configurado en el archivo `memory.max` del subsistema *cgroups*, el subsistema de memoria del kernel de Linux invocó al mecanismo **OOM Killer** (*Out of Memory Killer*).
- **`ExitCode: 137`**: En sistemas POSIX, un código de salida mayor a 128 indica terminación forzada por una señal del kernel: `128 + Señal`. En este caso: `128 + 9 = 137`, donde la señal 9 es **SIGKILL**, enviada directamente por el kernel al proceso sin posibilidad de captura o manejo por parte de la JVM.

---

### 4.2 Calibración del Límite Operativo Mínimo

Se realizó un procedimiento de calibración empírica iterativa ajustando el flag `-m` para identificar el umbral mínimo exacto que cumpla con:
1. Inicio correcto del servicio.
2. Arranque confirmado en los logs en **menos de 15 segundos** (`docker logs --timestamps eco_air_active | grep "Started EcoAirApplication"`).
3. Estabilidad en caliente comprobada con `docker stats --no-stream`.

#### Matriz de Pruebas Iterativas de Memoria (cgroups):

| Límite `-m` | Estado | Tiempo de Arranque | CPU % en Reposo | Uso Memoria / Límite | % Memoria | Diagnóstico Forense |
| :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **48 MB** | Exited | N/A (Crash) | 0% | N/A | N/A | **Colapso Inmediato.** El kernel envió SIGKILL (`OOMKilled: true`). |
| **96 MB** | Running | Inestable (> 25s) | 73.04% | 95.89MiB / 96MiB | **99.89%** | **Fallo por Latencia.** GC Thrashing constante, no cumple < 15 segundos. |
| **128 MB** | Running | 7.992 s | 0.13% | 118.1MiB / 128MiB | **92.23%** | **Inseguro.** Margen residual menor a 10 MB. Riesgo de OOM ante ráfagas. |
| **256 MB** | **Running** | **5.143 s** | **0.12%** | **212.3MiB / 256MiB** | **82.93%** | **UMBRAL MÍNIMO CALIBRADO.** Cumple todos los criterios de estabilidad. |
| **384 MB** | Running | 5.338 s | 0.24% | 215.7MiB / 384MiB | 56.16% | **Óptimo para Picos.** Margen holgado para ráfagas continuas de telemetría. |
| **512 MB** | Running | 6.386 s | 0.13% | 243.5MiB / 512MiB | 47.56% | **Sobre-dimensionado.** Consumo bajo el 50% de la cuota asignada. |

#### Evidencia de la Iteración Calibrada Mínima (256 MB):
```bash
# Verificación de tiempo de arranque (< 15 segundos):
$ docker logs --timestamps eco_air_active | grep "Started EcoAirApplication"
2026-10-20T23:44:00.123Z  INFO 1 --- [eco-air-backend] [           main] com.ecoair.EcoAirApplication             : Started EcoAirApplication in 5.143 seconds (process running for 5.72)

# Profiling en caliente (docker stats):
$ docker stats --no-stream eco_air_active
CONTAINER ID   NAME             CPU %     MEM USAGE / LIMIT   MEM %     NET I/O       BLOCK I/O     PIDS
8b4fbcc8f290   eco_air_active   0.12%     212.3MiB / 256MiB   82.93%    872B / 126B   46.4MB / 0B   31
```

**Conclusión de Calibración:**
El **umbral mínimo exacto** es **256 MB**. A este valor:
- La aplicación arranca en **5.14 segundos**, muy por debajo de la cota máxima tolerada de 15 segundos.
- La JVM aprovecha la ergonomía de contenedores (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`), asignando hasta el 75% del cgroup al Heap y reservando el resto para el runtime nativo y Metaspace.
- La memoria se estabiliza al **82.93%**, evitando el estrangulamiento de CPU y garantizando la absorción de telemetría crítica de sensores ambientales.

---

## 5. Conclusiones y Cumplimiento Normativo
1. **Seguridad en Runtime:** Se eliminó por completo el vector de ejecución como `root`. Cualquier intrusión queda restringida por los límites DAC del sistema operativo huésped.
2. **Defensa en Profundidad:** Al omitir el JDK y el código fuente en la imagen de producción mediante construcción multietapa, se redujo la superficie de ataque y el peso de la imagen en más de un 70%.
3. **Resiliencia Operativa:** Mediante la calibración de *cgroups*, se previene la denegación de servicio (*DoS*) sobre el host de la estación meteorológica, garantizando que el contenedor opere dentro de los límites estrictos de hardware asignados.
