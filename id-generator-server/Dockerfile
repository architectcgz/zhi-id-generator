FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="Platform Team"
LABEL description="Distributed ID Generator Service"

# Create app directory
WORKDIR /app

# Create data directory for worker ID cache
RUN mkdir -p /data/leaf && chmod 755 /data/leaf

# Copy the jar file
COPY target/id-generator-server-*.jar app.jar

# Environment variables with defaults
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV DB_HOST=localhost
ENV DB_PORT=5434
ENV DB_USER=id_gen_user
ENV DB_PASSWORD=id_gen_password
ENV ZOOKEEPER_CONNECTION_STRING=localhost:2181
ENV DATACENTER_ID=0
ENV ENABLE_ZOOKEEPER=true
ENV WORKER_ID_CACHE_PATH=/data/leaf/workerID.properties

# Expose port
EXPOSE 8010

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${SERVER_PORT:-8010}/api/v1/id/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
