package dev.eliascosta.invoice.common;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Test-only root configuration: {@code common} is a library without a
 * {@code @SpringBootApplication}, and Boot test slices ({@code @WebMvcTest}, {@code @JsonTest},
 * {@code @SpringBootTest}) need a {@code @SpringBootConfiguration} above the test package.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class CommonTestApplication {}
