package com.booking.integration;

import com.booking.MovieBookingApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

/**
 * End-to-end integration tests covering the core flows:
 *  - Admin builds catalog
 *  - Customer holds & books seats
 *  - Concurrent booking attempts only succeed for one user
 *  - Discount + refund policies behave correctly
 */
@SpringBootTest(classes = MovieBookingApplication.class)
@TestPropertySource(properties = {"booking.hold.duration-seconds=2"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookingFlowIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper om;
    private MockMvc mvc;

    private Long cityId, theaterId, screenId, movieId, showId;
    private List<Long> showSeatIds;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private String json(Object o) throws Exception { return om.writeValueAsString(o); }

    private JsonNode doPost(String url, String user, String pass, String body, int expected) throws Exception {
        MvcResult r = mvc.perform(post(url).with(httpBasic(user, pass))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn();
        String resp = r.getResponse().getContentAsString();
        return resp.isEmpty() ? null : om.readTree(resp);
    }

    private JsonNode doGet(String url, String user, String pass) throws Exception {
        MvcResult r = mvc.perform(get(url).with(httpBasic(user, pass)))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString());
    }

    @Test @Order(1)
    void e2e_admin_setup_then_customer_book_and_cancel() throws Exception {
        // ---- ADMIN: build catalog
        cityId = doPost("/api/admin/cities", "admin", "admin123",
                "{\"name\":\"Bengaluru-" + System.nanoTime() + "\"}", 201).get("id").asLong();

        theaterId = doPost("/api/admin/theaters", "admin", "admin123",
                json(java.util.Map.of("name","PVR Forum","address","Koramangala","cityId",cityId)), 201)
                .get("id").asLong();

        String screenBody = "{\"name\":\"Screen 1\",\"theaterId\":" + theaterId + "," +
                "\"seatLayout\":[" +
                "{\"rowLabel\":\"A\",\"seatsCount\":3,\"category\":\"REGULAR\"}," +
                "{\"rowLabel\":\"B\",\"seatsCount\":2,\"category\":\"PREMIUM\"}]}";
        screenId = doPost("/api/admin/screens", "admin", "admin123", screenBody, 201).get("id").asLong();

        movieId = doPost("/api/admin/movies", "admin", "admin123",
                "{\"title\":\"Dune 3\",\"durationMinutes\":160,\"language\":\"EN\",\"genre\":\"SciFi\"}", 201)
                .get("id").asLong();

        String showBody = "{\"movieId\":" + movieId + ",\"screenId\":" + screenId + "," +
                "\"startTime\":\"" + java.time.LocalDateTime.now().plusDays(2).withNano(0) + "\"," +
                "\"basePriceRegular\":200.00,\"basePricePremium\":400.00,\"pricingTier\":\"WEEKEND\"}";
        showId = doPost("/api/admin/shows", "admin", "admin123", showBody, 201).get("id").asLong();

        // Refund policy: >=24h before show => 80% refund
        doPost("/api/admin/refund-policies", "admin", "admin123",
                "{\"name\":\"Standard\",\"hoursBeforeShow\":24,\"refundPercentage\":80}", 201);

        // Discount code: 10% off
        doPost("/api/admin/discount-codes", "admin", "admin123",
                "{\"code\":\"WELCOME10\",\"percentage\":10," +
                        "\"validFrom\":\"" + java.time.LocalDateTime.now().minusDays(1) + "\"," +
                        "\"validTo\":\"" + java.time.LocalDateTime.now().plusDays(30) + "\"}", 201);

        // ---- CUSTOMER: list seats then hold
        JsonNode seats = doGet("/api/catalog/shows/" + showId + "/seats", "customer", "customer123");
        assertEquals(5, seats.size());
        showSeatIds = new ArrayList<>();
        showSeatIds.add(seats.get(0).get("id").asLong()); // A1 - REGULAR (200 * 1.25 = 250)
        showSeatIds.add(seats.get(3).get("id").asLong()); // B1 - PREMIUM (400 * 1.25 = 500)

        JsonNode hold = doPost("/api/bookings/hold", "customer", "customer123",
                "{\"showId\":" + showId + ",\"showSeatIds\":" + showSeatIds + "}", 201);
        Long bookingId = hold.get("id").asLong();
        assertEquals("PENDING", hold.get("status").asText());
        assertEquals(750.00, hold.get("subtotal").asDouble(), 0.01);

        // Apply discount
        JsonNode disc = doPost("/api/bookings/" + bookingId + "/apply-discount", "customer", "customer123",
                "{\"code\":\"WELCOME10\"}", 200);
        assertEquals(75.00, disc.get("discountAmount").asDouble(), 0.01);
        assertEquals(675.00, disc.get("totalAmount").asDouble(), 0.01);

        // Pay
        JsonNode paid = doPost("/api/bookings/" + bookingId + "/pay", "customer", "customer123", "", 200);
        assertEquals("CONFIRMED", paid.get("status").asText());

        // Cancel — show is 2 days away, policy is >=24h => 80% refund => 540.00
        JsonNode cancel = doPost("/api/bookings/" + bookingId + "/cancel", "customer", "customer123", "", 200);
        assertEquals("CANCELLED", cancel.get("status").asText());
        assertEquals(540.00, cancel.get("refundAmount").asDouble(), 0.01);

        // Seats freed
        JsonNode seatsAfter = doGet("/api/catalog/shows/" + showId + "/seats", "customer", "customer123");
        long booked = 0;
        for (JsonNode n : seatsAfter) if ("BOOKED".equals(n.get("status").asText())) booked++;
        assertEquals(0, booked);
    }

    @Test @Order(2)
    void concurrent_bookings_for_same_seat_only_one_succeeds() throws Exception {
        // Reuse admin to create a fresh isolated show
        long cId = doPost("/api/admin/cities", "admin", "admin123",
                "{\"name\":\"CityC-" + System.nanoTime() + "\"}", 201).get("id").asLong();
        long tId = doPost("/api/admin/theaters", "admin", "admin123",
                json(java.util.Map.of("name","T","address","X","cityId", cId)), 201).get("id").asLong();
        String sb = "{\"name\":\"S\",\"theaterId\":" + tId + ",\"seatLayout\":[" +
                "{\"rowLabel\":\"A\",\"seatsCount\":2,\"category\":\"REGULAR\"}]}";
        long scId = doPost("/api/admin/screens", "admin", "admin123", sb, 201).get("id").asLong();
        long mId = doPost("/api/admin/movies", "admin", "admin123",
                "{\"title\":\"M2\",\"durationMinutes\":100}", 201).get("id").asLong();
        String body = "{\"movieId\":" + mId + ",\"screenId\":" + scId + ",\"startTime\":\""
                + java.time.LocalDateTime.now().plusDays(1).withNano(0)
                + "\",\"basePriceRegular\":100.00,\"basePricePremium\":200.00,\"pricingTier\":\"REGULAR\"}";
        long shId = doPost("/api/admin/shows", "admin", "admin123", body, 201).get("id").asLong();

        JsonNode seats = doGet("/api/catalog/shows/" + shId + "/seats", "customer", "customer123");
        long targetSeat = seats.get(0).get("id").asLong();

        // Two concurrent hold requests on the same seat
        int n = 8;
        ExecutorService ex = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            results.add(ex.submit(() -> {
                start.await();
                MvcResult r = mvc.perform(post("/api/bookings/hold").with(httpBasic("customer","customer123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"showId\":" + shId + ",\"showSeatIds\":[" + targetSeat + "]}"))
                        .andReturn();
                return r.getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0, conflict = 0, other = 0;
        for (Future<Integer> f : results) {
            int s = f.get(10, TimeUnit.SECONDS);
            if (s == 201) created++;
            else if (s == 409) conflict++;
            else other++;
        }
        ex.shutdown();
        assertEquals(1, created, "Exactly one hold should succeed");
        assertEquals(n - 1, conflict, "All others must get 409 conflict");
        assertEquals(0, other);
    }

    @Test @Order(3)
    void expired_hold_releases_seat_for_new_hold() throws Exception {
        long cId = doPost("/api/admin/cities", "admin", "admin123",
                "{\"name\":\"CityE-" + System.nanoTime() + "\"}", 201).get("id").asLong();
        long tId = doPost("/api/admin/theaters", "admin", "admin123",
                json(java.util.Map.of("name","TE","address","X","cityId", cId)), 201).get("id").asLong();
        long scId = doPost("/api/admin/screens", "admin", "admin123",
                "{\"name\":\"SE\",\"theaterId\":" + tId + ",\"seatLayout\":[" +
                        "{\"rowLabel\":\"A\",\"seatsCount\":1,\"category\":\"REGULAR\"}]}", 201)
                .get("id").asLong();
        long mId = doPost("/api/admin/movies", "admin", "admin123",
                "{\"title\":\"M3\",\"durationMinutes\":100}", 201).get("id").asLong();
        long shId = doPost("/api/admin/shows", "admin", "admin123",
                "{\"movieId\":" + mId + ",\"screenId\":" + scId + ",\"startTime\":\""
                        + java.time.LocalDateTime.now().plusDays(1).withNano(0)
                        + "\",\"basePriceRegular\":100,\"basePricePremium\":200,\"pricingTier\":\"REGULAR\"}", 201)
                .get("id").asLong();
        long seatId = doGet("/api/catalog/shows/" + shId + "/seats", "customer","customer123").get(0).get("id").asLong();

        // Hold with 2s expiry (set via @TestPropertySource)
        doPost("/api/bookings/hold", "customer", "customer123",
                "{\"showId\":" + shId + ",\"showSeatIds\":[" + seatId + "]}", 201);

        // Second hold immediately => conflict
        mvc.perform(post("/api/bookings/hold").with(httpBasic("customer","customer123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + shId + ",\"showSeatIds\":[" + seatId + "]}"))
                .andExpect(status().is(409));

        // Wait for hold to expire (>2s)
        Thread.sleep(2500);

        // After expiry the holdSeats logic itself treats expired-HELD as available, so should succeed
        mvc.perform(post("/api/bookings/hold").with(httpBasic("customer","customer123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + shId + ",\"showSeatIds\":[" + seatId + "]}"))
                .andExpect(status().is(201));
    }
}
