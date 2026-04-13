package com.okta.scim.it;

import org.apache.http.impl.client.HttpClients;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Base class for SCIM integration tests.
 *
 * Starts the full Spring Boot application on a random port backed by an
 * in-memory HSQLDB (profile "test").  Provides helpers for JSON-Schema
 * validation against the files in src/test/resources/schema/.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class ScimIntegrationTestBase {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    // -------------------------------------------------------------------------
    // Setup — configure Apache HttpClient so PATCH requests work
    // -------------------------------------------------------------------------

    @BeforeEach
    void configureApacheHttpClient() {
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(HttpClients.createDefault());
        restTemplate.getRestTemplate().setRequestFactory(factory);
    }

    // -------------------------------------------------------------------------
    // URL helper
    // -------------------------------------------------------------------------

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    // -------------------------------------------------------------------------
    // JSON-Schema validation helpers
    // -------------------------------------------------------------------------

    /**
     * Validates {@code jsonBody} against the JSON-Schema file located at
     * {@code src/test/resources/schema/<schemaFileName>}.
     * Fails the test if any violation is found.
     */
    protected void assertValidatesSchema(String jsonBody, String schemaFileName) {
        try (InputStream schemaStream = getClass().getResourceAsStream("/schema/" + schemaFileName)) {
            if (schemaStream == null) {
                fail("Schema file not found on classpath: /schema/" + schemaFileName);
            }
            String schemaContent = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject rawSchema = new JSONObject(schemaContent);
            Schema schema = SchemaLoader.load(rawSchema);
            schema.validate(new JSONObject(jsonBody));
        } catch (ValidationException e) {
            fail("JSON Schema validation failed [" + schemaFileName + "]: "
                    + e.getMessage() + "\nAll violations: " + e.getAllMessages());
        } catch (Exception e) {
            fail("Unexpected error during schema validation [" + schemaFileName + "]: " + e.getMessage());
        }
    }

    /**
     * Validates a JSON array body against the given schema.
     * Prefer {@link #assertValidatesSchema(String, String)} for object roots.
     */
    protected void assertArrayValidatesSchema(String jsonArrayBody, String schemaFileName) {
        try (InputStream schemaStream = getClass().getResourceAsStream("/schema/" + schemaFileName)) {
            if (schemaStream == null) {
                fail("Schema file not found on classpath: /schema/" + schemaFileName);
            }
            String schemaContent = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject rawSchema = new JSONObject(schemaContent);
            Schema schema = SchemaLoader.load(rawSchema);
            schema.validate(new JSONArray(jsonArrayBody));
        } catch (ValidationException e) {
            fail("JSON Schema validation failed [" + schemaFileName + "]: "
                    + e.getMessage() + "\nAll violations: " + e.getAllMessages());
        } catch (Exception e) {
            fail("Unexpected error during schema validation [" + schemaFileName + "]: " + e.getMessage());
        }
    }
}
