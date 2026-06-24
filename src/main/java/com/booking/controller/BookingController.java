package com.booking.controller;

import com.booking.dto.Requests;
import com.booking.dto.Responses;
import com.booking.service.AuthHelper;
import com.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final AuthHelper authHelper;

    public BookingController(BookingService bookingService, AuthHelper authHelper) {
        this.bookingService = bookingService;
        this.authHelper = authHelper;
    }

    @PostMapping("/hold")
    public ResponseEntity<Responses.BookingResponse> hold(@Valid @RequestBody Requests.HoldSeatsRequest req) {
        var b = bookingService.holdSeats(authHelper.currentUser(), req.showId(), req.showSeatIds());
        return ResponseEntity.status(201).body(Responses.BookingResponse.from(b));
    }

    @PostMapping("/{id}/apply-discount")
    public Responses.BookingResponse applyDiscount(@PathVariable Long id, @Valid @RequestBody Requests.ApplyDiscountRequest req) {
        return Responses.BookingResponse.from(bookingService.applyDiscount(authHelper.currentUser(), id, req.code()));
    }

    @PostMapping("/{id}/pay")
    public Responses.BookingResponse pay(@PathVariable Long id) {
        return Responses.BookingResponse.from(bookingService.confirmAndPay(authHelper.currentUser(), id));
    }

    @PostMapping("/{id}/cancel")
    public Responses.BookingResponse cancel(@PathVariable Long id) {
        return Responses.BookingResponse.from(bookingService.cancel(authHelper.currentUser(), id));
    }

    @GetMapping("/my")
    public List<Responses.BookingResponse> myBookings() {
        return bookingService.myBookings(authHelper.currentUser())
                .stream().map(Responses.BookingResponse::from).toList();
    }

    @GetMapping("/{id}")
    public Responses.BookingResponse get(@PathVariable Long id) {
        return Responses.BookingResponse.from(bookingService.getBooking(authHelper.currentUser(), id));
    }
}
