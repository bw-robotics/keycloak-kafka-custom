FROM quay.io/keycloak/keycloak:26.1.2
ADD --chown=keycloak:keycloak --chmod=644 \
    keycloak-kafka-1.2.0-jar-with-dependencies.jar \
    /opt/keycloak/providers/
RUN echo " \
security.protocol=${KAFKA_SECURITY_PROTOCOL} \n\
sasl.mechanism=${KAFKA_SASL_MECHANISM} \n\
sasl.jaas.config=${KAFKA_SASL_JAAS_CONFIG} \n\
sasl.client.callback.handler.class=${KAFKA_SASL_CLIENT_CALLBACK_HANDLER_CLASS}" > /opt/keycloak/conf/kafka.properties
RUN /opt/keycloak/bin/kc.sh build

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
