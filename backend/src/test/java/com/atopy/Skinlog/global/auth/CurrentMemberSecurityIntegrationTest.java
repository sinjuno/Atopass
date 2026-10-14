package com.atopy.Skinlog.global.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CurrentMemberSecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void noHeader_returns401WithErrorResponseBody() throws Exception {
        mockMvc.perform(get("/__test/current-member"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void nonNumericHeader_returns401WithErrorResponseBody() throws Exception {
        mockMvc.perform(get("/__test/current-member").header("X-Member-Id", "abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void validHeader_resolvesAndReturns200() throws Exception {
        mockMvc.perform(get("/__test/current-member").header("X-Member-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }
}
