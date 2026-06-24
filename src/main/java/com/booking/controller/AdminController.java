package com.booking.controller;

import com.booking.dto.Requests;
import com.booking.dto.Responses;
import com.booking.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/cities")
    public ResponseEntity<Responses.CityResponse> createCity(@Valid @RequestBody Requests.CityRequest req) {
        return ResponseEntity.status(201).body(Responses.CityResponse.from(adminService.createCity(req)));
    }

    @PostMapping("/theaters")
    public ResponseEntity<Responses.TheaterResponse> createTheater(@Valid @RequestBody Requests.TheaterRequest req) {
        return ResponseEntity.status(201).body(Responses.TheaterResponse.from(adminService.createTheater(req)));
    }

    @PostMapping("/screens")
    public ResponseEntity<Responses.ScreenResponse> createScreen(@Valid @RequestBody Requests.ScreenRequest req) {
        var screen = adminService.createScreen(req);
        return ResponseEntity.status(201).body(Responses.ScreenResponse.from(screen, adminService.seatsForScreen(screen.getId())));
    }

    @PostMapping("/movies")
    public ResponseEntity<Responses.MovieResponse> createMovie(@Valid @RequestBody Requests.MovieRequest req) {
        return ResponseEntity.status(201).body(Responses.MovieResponse.from(adminService.createMovie(req)));
    }

    @PostMapping("/shows")
    public ResponseEntity<Responses.ShowResponse> createShow(@Valid @RequestBody Requests.ShowRequest req) {
        return ResponseEntity.status(201).body(Responses.ShowResponse.from(adminService.createShow(req)));
    }

    @PostMapping("/discount-codes")
    public ResponseEntity<?> createDiscount(@Valid @RequestBody Requests.DiscountCodeRequest req) {
        return ResponseEntity.status(201).body(adminService.createDiscountCode(req));
    }

    @PostMapping("/refund-policies")
    public ResponseEntity<?> createRefundPolicy(@Valid @RequestBody Requests.RefundPolicyRequest req) {
        return ResponseEntity.status(201).body(adminService.createRefundPolicy(req));
    }
}
