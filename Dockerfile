FROM eclipse-temurin:25-jre-noble
LABEL authors="laasilva" \
      description="dracolich-ai-api" \
      version="1.0" \
      org.opencontainers.image.vendor="dracolich" \
      org.opencontainers.image.title="Dracolich AI API"

EXPOSE 8080
EXPOSE 7980

RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

RUN useradd -r -s /bin/false -U -d /opt/mtg-ai appuser \
    && mkdir -p /opt/mtg-ai \
    && chown -R appuser:appuser /opt/mtg-ai

USER appuser
WORKDIR /opt/mtg-ai

COPY --chown=appuser:appuser dracolich-ai-web/target/dracolich-ai-web.jar /opt/mtg-ai/mtg-ai.jar

HEALTHCHECK --interval=60s --timeout=5s --start-period=120s --retries=3 \
    CMD curl -fsS http://localhost:7980/actuator/health || exit 1

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/opt/mtg-ai/mtg-ai.jar"]