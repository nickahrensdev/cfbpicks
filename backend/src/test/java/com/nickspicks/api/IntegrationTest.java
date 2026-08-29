package com.nickspicks.api;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need the real schema.
 *
 * <p>A Postgres container rather than H2, because the migrations use Postgres
 * syntax (aggregate {@code filter} clauses, {@code gen_random_uuid()}, row
 * level security) that H2 cannot execute - testing against a database that
 * accepts different DDL would prove very little. Requires Docker.
 *
 * <p>One container is shared by every test class: starting Postgres per class
 * would dominate the run time, and each test cleans up after itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void resetData() {
        cleanUp();
    }

    /** Subclasses wipe whatever they inserted. */
    protected abstract void cleanUp();
}
