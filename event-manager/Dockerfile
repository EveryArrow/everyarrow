FROM eclipse-temurin:21.0.11_10-jre-ubi10-minimal@sha256:4863eff777ff4da1b3bf4261e571c20ef5ee4be37aa8c8dfba88bc01269e5eca

ARG JAR_FILE=target/event-engine-1.0.jar

WORKDIR /opt/everyarrow/event-manager

COPY --chown=10001:10001 ${JAR_FILE} event-engine.jar
COPY --chown=10001:10001 docker/entrypoint.sh /usr/local/bin/everyarrow-event-manager

RUN chmod +x /usr/local/bin/everyarrow-event-manager \
    && mkdir -p /opt/everyarrow/event-manager/config \
    && mkdir -p /opt/everyarrow/event-manager/plugin-jars \
    && mkdir -p /opt/everyarrow/event-manager/plugins \
    && mkdir -p /var/log/everyarrow/event-manager \
    && chown -R 10001:10001 /opt/everyarrow /var/log/everyarrow

ENV EVENT_ENGINE_HTTP_HOST=0.0.0.0
ENV EVENT_ENGINE_HTTP_PORT=8080
ENV EA_EVENT_MANAGER_MODE=local
ENV EA_LOG_ROOT=/var/log/everyarrow
ENV EA_PLUGIN_ROOT=/opt/everyarrow/event-manager/plugins

EXPOSE 8080

USER 10001:10001

ENTRYPOINT ["everyarrow-event-manager"]
