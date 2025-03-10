package com.github.snuk87.keycloak.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.snuk87.keycloak.kafka.model.KafkaRealmConfig;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.keycloak.events.Details.NOT_BEFORE;
import static org.keycloak.events.EventType.USER_DISABLED_BY_TEMPORARY_LOCKOUT;

public class KafkaEventListenerProvider implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(KafkaEventListenerProvider.class);

    private String topicEvents;

    private List<EventType> events;

    private String topicAdminEvents;

    private Map<String, Producer<String, String>> producerByRealm = new HashMap<>();

    private ObjectMapper mapper;

    public KafkaEventListenerProvider(Map<String, KafkaRealmConfig> kafkaConfigByRealm, String topicEvents, String[] events,
                                      String topicAdminEvents, Map<String, Object> kafkaProducerProperties, KafkaProducerFactory factory) {
        this.topicEvents = topicEvents;
        this.events = new ArrayList<>();
        this.topicAdminEvents = topicAdminEvents;

        for (String event : events) {
            try {
                EventType eventType = EventType.valueOf(event.toUpperCase());
                this.events.add(eventType);
            } catch (IllegalArgumentException e) {
                LOG.debug("Ignoring event >" + event + "<. Event does not exist.");
            }
        }

        LOG.info("Populating " + kafkaConfigByRealm.size() + " Kafka producers by realm...");
        populateProducerByRealmMap(kafkaConfigByRealm, factory, kafkaProducerProperties);
        LOG.info("Populated " + producerByRealm.size() + " producers");
        mapper = new ObjectMapper();
    }

    @Override
    public void onEvent(Event event) {
        final EventType type = event.getType();
        if (events.contains(type)) {
            if (USER_DISABLED_BY_TEMPORARY_LOCKOUT.equals(type)) {
                changeUnlockTimeType(event.getDetails());
            }
            try {
                produceEvent(mapper.writeValueAsString(event), topicEvents, event.getRealmName());
            } catch (JsonProcessingException | ExecutionException | TimeoutException e) {
                LOG.error(e.getMessage(), e);
            } catch (InterruptedException e) {
                LOG.error(e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        if (topicAdminEvents != null) {
            try {
                produceEvent(mapper.writeValueAsString(event), topicAdminEvents, event.getRealmName());
            } catch (JsonProcessingException | ExecutionException | TimeoutException e) {
                LOG.error(e.getMessage(), e);
            } catch (InterruptedException e) {
                LOG.error(e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        // ignore
    }

    private void populateProducerByRealmMap(Map<String, KafkaRealmConfig> kafkaConfigByRealm,
                                            KafkaProducerFactory factory,
                                            Map<String, Object> kafkaProducerProperties) {
        kafkaConfigByRealm.forEach((realm, config) -> {
            final Producer<String, String> producer = factory.createProducer(config.getClientId(), config.getBootstrapServers(), kafkaProducerProperties);
            this.producerByRealm.put(realm, producer);
        });

    }

    private void produceEvent(String eventAsString, String topic, String realmName)
            throws InterruptedException, ExecutionException, TimeoutException {
        LOG.debug("Produce to topic: " + topicEvents + " ...");
        final Producer<String, String> producer = producerByRealm.get(realmName);

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, eventAsString);
        Future<RecordMetadata> metaData = producer.send(record);
        RecordMetadata recordMetadata = metaData.get(30, TimeUnit.SECONDS);
        LOG.debug("Produced to topic: " + recordMetadata.topic());
    }

    private void changeUnlockTimeType(Map<String, String> details) {
        final String notBeforeString = details.get(NOT_BEFORE);

        LocalDateTime localDateTime = LocalDateTime.parse(notBeforeString);
        ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.of(ZoneId.systemDefault().getId()));

        final long epochSecond = zonedDateTime.toInstant().getEpochSecond();
        details.put(NOT_BEFORE, Long.toString(epochSecond));
    }
}
