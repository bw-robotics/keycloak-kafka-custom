package com.github.snuk87.keycloak.kafka;

import com.github.snuk87.keycloak.kafka.model.KafkaRealmConfig;
import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class KafkaEventListenerProviderFactory implements EventListenerProviderFactory {

    private static final Logger LOG = Logger.getLogger(KafkaEventListenerProviderFactory.class);
    private static final String ID = "kafka";
    private static final String PREFIX = "KAFKA_REALMS_";

    private KafkaEventListenerProvider instance;

    private String topicEvents;
    private String topicAdminEvents;
    private String[] events;
    private Map<String, KafkaRealmConfig> kafkaConfigByRealm;
    private Map<String, Object> kafkaProducerProperties;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        if (instance == null) {
            instance = new KafkaEventListenerProvider(kafkaConfigByRealm, topicEvents, events, topicAdminEvents,
                    kafkaProducerProperties, new KafkaStandardProducerFactory());
        }

        return instance;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void init(Scope config) {
        LOG.info("Init kafka module ...");
        topicEvents = config.get("topicEvents", System.getenv("KAFKA_TOPIC"));
        topicAdminEvents = config.get("topicAdminEvents", System.getenv("KAFKA_ADMIN_TOPIC"));

        kafkaConfigByRealm = loadKafkaByRealmConfig();

        String eventsString = config.get("events", System.getenv("KAFKA_EVENTS"));

        if (eventsString != null) {
            events = eventsString.split(",");
        }

        if (topicEvents == null) {
            throw new NullPointerException("topic must not be null.");
        }

        if (events == null || events.length == 0) {
            events = new String[1];
            events[0] = "REGISTER";
        }

        kafkaProducerProperties = KafkaProducerConfig.init(config);
    }

    @Override
    public void postInit(KeycloakSessionFactory arg0) {
        // ignore
    }

    @Override
    public void close() {
        // ignore
    }

    private Map<String, KafkaRealmConfig> loadKafkaByRealmConfig() {
        final Map<String, KafkaRealmConfig> configMap = new HashMap<>();
        final Map<String, String> realmNameMap = new HashMap<>();

        final Map<String, String> kafkaEnvVars = System.getenv().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(PREFIX))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        LOG.info("Loading kafka realm configs: " + kafkaEnvVars.size());

        // Step 1: Extract Realm Names
        for (Map.Entry<String, String> entry : kafkaEnvVars.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Check if the key contains "_REALM_NAME"
            if (key.endsWith("_REALM_NAME")) {
                String realmKey = key.substring(PREFIX.length(), key.length() - "_REALM_NAME".length());
                realmNameMap.put(realmKey, value.toLowerCase());  // Store the realm name as the key
            }
        }

        for (Map.Entry<String, String> entry : kafkaEnvVars.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Ignore REALM_NAME variables, they are already processed
            if (key.endsWith("_REALM_NAME")) continue;

            String[] parts = key.substring(PREFIX.length()).split("_", 2);
            if (parts.length < 2) continue;

            String realmKey = parts[0];  // This is the identifier (e.g., "DEV", "QA")
            String property = parts[1].toLowerCase().replace('_', '-'); // Convert to standard property format

            // Get the real realm name from realmNameMap
            String realmName = realmNameMap.getOrDefault(realmKey, realmKey.toLowerCase());

            KafkaRealmConfig config = configMap.getOrDefault(realmName, new KafkaRealmConfig());

            if ("bootstrap-servers".equals(property)) {
                if (value == null) {
                    throw new NullPointerException("bootstrapServers must not be null");
                }
                config.setBootstrapServers(value);
            } else if ("client-id".equals(property)) {
                if (value == null) {
                    throw new NullPointerException("clientId must not be null");
                }
                config.setClientId(value);
            }

            configMap.put(realmName, config);
        }

        return configMap;
    }
}
