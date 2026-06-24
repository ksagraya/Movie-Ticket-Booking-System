package com.booking.integration;

import com.booking.MovieBookingApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MovieBookingApplication.class)
class SecurityRbacIntegrationTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Test
    void anonymous_cannot_create_city() throws Exception {
        mvc.perform(post("/api/admin/cities").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void customer_cannot_access_admin() throws Exception {
        mvc.perform(post("/api/admin/cities").with(httpBasic("customer","customer123"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_access_admin() throws Exception {
        mvc.perform(post("/api/admin/cities").with(httpBasic("admin","admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CityX-" + System.nanoTime() + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void customer_can_register_publicly() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user" + System.nanoTime() + "\",\"password\":\"secret123\"," +
                        "\"email\":\"a@b.com\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void invalid_input_returns_400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"x\",\"email\":\"not-email\"}"))
                .andExpect(status().isBadRequest());
    }
}
