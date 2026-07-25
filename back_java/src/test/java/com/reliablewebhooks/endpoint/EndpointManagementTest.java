package com.reliablewebhooks.endpoint;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reliablewebhooks.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class EndpointManagementTest extends AbstractIntegrationTest {

    @Test
    void registersAnEndpointAndReturnsItsSecretExactlyOnce() throws Exception {
        MvcResult created = mockMvc.perform(post("/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com/hook","description":"test endpoint"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.secret", notNullValue()))
                .andExpect(jsonPath("$.circuitBreakerState").value("CLOSED"))
                .andExpect(jsonPath("$.successCount").value(0))
                .andExpect(jsonPath("$.deadCount").value(0))
                .andExpect(jsonPath("$.successRate").doesNotExist())
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH))
                .andReturn();

        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/endpoints/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }

    @Test
    void listsRegisteredEndpointsWithoutSecrets() throws Exception {
        mockMvc.perform(post("/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com/list-me"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/endpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].secret").doesNotExist())
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }

    @Test
    void deletesARegisteredEndpoint() throws Exception {
        MvcResult created = mockMvc.perform(post("/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com/delete-me"}"""))
                .andExpect(status().isCreated())
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/endpoints/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));

        mockMvc.perform(get("/endpoints/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }

    @Test
    void deletingAnUnknownEndpointReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/endpoints/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(OPENAPI_SPEC_PATH));
    }

    @Test
    void rejectsAnEndpointCreateRequestMissingTheRequiredUrlField() throws Exception {
        // No contract-validation assertion here: the request is deliberately
        // schema-invalid (that's what's under test), and this library's
        // openApi().isValid() checks the request and response together —
        // it can never pass against an intentionally invalid request.
        mockMvc.perform(post("/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
