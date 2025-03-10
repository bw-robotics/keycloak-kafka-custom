package com.github.snuk87.keycloak.kafka.model;

public class KafkaRealmConfig {

    private String bootstrapServers;
    private String clientId;

    public KafkaRealmConfig() {

    }

    public KafkaRealmConfig(String bootstrapServers, String clientId) {
        this.bootstrapServers = bootstrapServers;
        this.clientId = clientId;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public String toString() {
        return "KafkaRealmConfig{" +
                "bootstrapServers='" + bootstrapServers + '\'' +
                ", clientId='" + clientId + '\'' +
                '}';
    }
}
