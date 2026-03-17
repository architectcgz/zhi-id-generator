FROM eclipse-temurin:17-jre

LABEL maintainer="Platform Team"
LABEL description="Distributed ID Generator Service"

# Create app directory
WORKDIR /app

# Create data directory for worker ID cache
RUN mkdir -p /data/leaf && chmod 755 /data/leaf

# Copy the executable Spring Boot jar built on the host
COPY id-generator-server/target/id-generator-server-*.jar app.jar

# Environment variables with defaults
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV DB_HOST=localhost
ENV DB_PORT=5435
ENV DB_USER=id_gen_user
ENV DB_PASSWORD=id_gen_password
ENV DATACENTER_ID=0
ENV WORKER_ID_CACHE_PATH=/data/leaf/workerID.properties

# Expose port
EXPOSE 8011

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:${SERVER_PORT:-8011}/api/v1/id/health | grep -q '"status":"UP"' || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
