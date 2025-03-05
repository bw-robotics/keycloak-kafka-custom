package com.github.snuk87.keycloak.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.github.snuk87.keycloak.kafka.model.KafkaRealmConfig;
import org.apache.kafka.clients.producer.MockProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;

class KafkaEventListenerProviderTests {

	private static final String DEV_REALM = "DEV";

	private KafkaEventListenerProvider listener;
	private KafkaProducerFactory factory;
	private Map<String, KafkaRealmConfig> configs = Map.of(DEV_REALM, new KafkaRealmConfig("localhost:9092", "testClientId"));

	@BeforeEach
	void setUp() throws Exception {
		factory = new KafkaMockProducerFactory();
		listener = new KafkaEventListenerProvider(configs, "", new String[] { "REGISTER" }, "admin-events", Map.of(),
				factory);
	}

	@Test
	void shouldProduceEventWhenTypeIsDefined() throws Exception {
		Event event = new Event();
		event.setType(EventType.REGISTER);
		event.setRealmName(DEV_REALM);
		MockProducer<?, ?> producer = getProducerUsingReflection();

		listener.onEvent(event);

		assertEquals(1, producer.history().size());
	}

	@Test
	void shouldDoNothingWhenTypeIsNotDefined() throws Exception {
		Event event = new Event();
		event.setType(EventType.CLIENT_DELETE);
		event.setRealmName(DEV_REALM);
		MockProducer<?, ?> producer = getProducerUsingReflection();

		listener.onEvent(event);

		assertTrue(producer.history().isEmpty());
	}

	@Test
	void shouldProduceEventWhenTopicAdminEventsIsNotNull() throws Exception {
		AdminEvent event = new AdminEvent();
		event.setRealmName(DEV_REALM);
		MockProducer<?, ?> producer = getProducerUsingReflection();

		listener.onEvent(event, false);

		assertEquals(1, producer.history().size());
	}

	@Test
	void shouldDoNothingWhenTopicAdminEventsIsNull() throws Exception {
		listener = new KafkaEventListenerProvider(configs, "", new String[] { "REGISTER" }, null, Map.of(), factory);
		AdminEvent event = new AdminEvent();
		event.setRealmName(DEV_REALM);
		MockProducer<?, ?> producer = getProducerUsingReflection();

		listener.onEvent(event, false);

		assertTrue(producer.history().isEmpty());
	}

	private MockProducer<?, ?> getProducerUsingReflection() throws Exception {
		Field producerByRealmField = KafkaEventListenerProvider.class.getDeclaredField("producerByRealm");
		producerByRealmField.setAccessible(true);
		Map<String, MockProducer> producerByRealmMap = (Map<String, MockProducer>) producerByRealmField.get(listener);
		return producerByRealmMap.get(DEV_REALM);
	}

}
