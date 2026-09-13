package dev.eliascosta.invoice.samplemessaging;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Stand-in for a real service used by the outbox/messaging integration test: it lives in its own
 * base package and does <em>not</em> scan {@code common}, so the test proves that the
 * auto-configurations alone provide the outbox, the processed-events guard, the codec, the error
 * handler and the topics. Kept apart from {@code sampleservice} so the idempotency test does not
 * start a Kafka listener.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
public class SampleMessagingApplication {}
