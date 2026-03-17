FROM eclipse-temurin:17-jre

LABEL maintainer="Platform Team"
LABEL description="Distributed ID Generator Service"

WORKDIR /app

RUN mkdir -p /data/leaf && chmod 755 /data/leaf

COPY id-generator-server/target/id-generator-server-*.jar app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV DB_HOST=localhost
ENV DB_PORT=5432
ENV DB_USER=postgres
ENV DB_PASSWORD=postgres123456
ENV DATACENTER_ID=0
ENV WORKER_ID_CACHE_PATH=/data/leaf/workerID.properties

EXPOSE 8011

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:${SERVER_PORT:-8011}/api/v1/id/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
