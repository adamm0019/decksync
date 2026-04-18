package dev.decksync;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only Spring Boot entry point for {@code :core}. After the M7a module split the real {@code
 * DeckSyncApplication} lives in {@code :cli}, so {@code @WebMvcTest} / {@code @SpringBootTest}
 * classes in {@code :core}'s test source set have nothing to walk upwards to when they look for a
 * {@code @SpringBootConfiguration}. This stub sits at the root package and restores that lookup,
 * scanning {@code dev.decksync.*} so every core bean is visible without the CLI/GUI layers.
 */
@SpringBootApplication
public class CoreTestApplication {}
