package com.booking.service;

import com.booking.dto.Requests;
import com.booking.entity.*;
import com.booking.exception.ApiException;
import com.booking.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AdminService {

    private final CityRepository cityRepository;
    private final TheaterRepository theaterRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final RefundPolicyRepository refundPolicyRepository;

    public AdminService(CityRepository cityRepository, TheaterRepository theaterRepository,
                        ScreenRepository screenRepository, SeatRepository seatRepository,
                        MovieRepository movieRepository, ShowRepository showRepository,
                        ShowSeatRepository showSeatRepository,
                        DiscountCodeRepository discountCodeRepository,
                        RefundPolicyRepository refundPolicyRepository) {
        this.cityRepository = cityRepository;
        this.theaterRepository = theaterRepository;
        this.screenRepository = screenRepository;
        this.seatRepository = seatRepository;
        this.movieRepository = movieRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.discountCodeRepository = discountCodeRepository;
        this.refundPolicyRepository = refundPolicyRepository;
    }

    @Transactional
    public City createCity(Requests.CityRequest req) {
        return cityRepository.save(City.builder().name(req.name()).build());
    }

    @Transactional
    public Theater createTheater(Requests.TheaterRequest req) {
        City city = cityRepository.findById(req.cityId())
                .orElseThrow(() -> new ApiException("City not found", 404));
        return theaterRepository.save(Theater.builder()
                .name(req.name()).address(req.address()).city(city).build());
    }

    @Transactional
    public Screen createScreen(Requests.ScreenRequest req) {
        Theater theater = theaterRepository.findById(req.theaterId())
                .orElseThrow(() -> new ApiException("Theater not found", 404));
        Screen screen = screenRepository.save(Screen.builder().name(req.name()).theater(theater).build());

        List<Seat> seats = new ArrayList<>();
        for (Requests.SeatLayoutRow row : req.seatLayout()) {
            for (int i = 1; i <= row.seatsCount(); i++) {
                seats.add(Seat.builder()
                        .screen(screen)
                        .rowLabel(row.rowLabel())
                        .seatNumber(i)
                        .category(row.category())
                        .build());
            }
        }
        seatRepository.saveAll(seats);
        return screen;
    }

    public List<Seat> seatsForScreen(Long screenId) {
        return seatRepository.findByScreenId(screenId);
    }

    @Transactional
    public Movie createMovie(Requests.MovieRequest req) {
        return movieRepository.save(Movie.builder()
                .title(req.title()).durationMinutes(req.durationMinutes())
                .language(req.language()).genre(req.genre()).build());
    }

    @Transactional
    public Show createShow(Requests.ShowRequest req) {
        Movie movie = movieRepository.findById(req.movieId())
                .orElseThrow(() -> new ApiException("Movie not found", 404));
        Screen screen = screenRepository.findById(req.screenId())
                .orElseThrow(() -> new ApiException("Screen not found", 404));

        Show show = showRepository.save(Show.builder()
                .movie(movie).screen(screen).startTime(req.startTime())
                .basePriceRegular(req.basePriceRegular())
                .basePricePremium(req.basePricePremium())
                .pricingTier(req.pricingTier()).build());

        // Materialize ShowSeat rows for every seat in the screen with tier-based pricing
        List<Seat> seats = seatRepository.findByScreenId(screen.getId());
        if (seats.isEmpty()) {
            throw new ApiException("Screen has no seats", 400);
        }
        List<ShowSeat> showSeats = new ArrayList<>();
        for (Seat seat : seats) {
            var price = PricingCalculator.priceFor(seat.getCategory(), req.basePriceRegular(),
                    req.basePricePremium(), req.pricingTier());
            showSeats.add(ShowSeat.builder()
                    .show(show).seat(seat)
                    .status(ShowSeatStatus.AVAILABLE)
                    .price(price)
                    .build());
        }
        showSeatRepository.saveAll(showSeats);
        return show;
    }

    @Transactional
    public DiscountCode createDiscountCode(Requests.DiscountCodeRequest req) {
        if (discountCodeRepository.findByCodeIgnoreCase(req.code()).isPresent()) {
            throw new ApiException("Discount code already exists", 409);
        }
        return discountCodeRepository.save(DiscountCode.builder()
                .code(req.code().toUpperCase(Locale.ROOT))
                .percentage(req.percentage())
                .maxDiscount(req.maxDiscount())
                .validFrom(req.validFrom()).validTo(req.validTo())
                .maxUses(req.maxUses()).usedCount(0)
                .active(req.active() == null ? Boolean.TRUE : req.active())
                .build());
    }

    @Transactional
    public RefundPolicy createRefundPolicy(Requests.RefundPolicyRequest req) {
        return refundPolicyRepository.save(RefundPolicy.builder()
                .name(req.name())
                .hoursBeforeShow(req.hoursBeforeShow())
                .refundPercentage(req.refundPercentage())
                .active(req.active() == null ? Boolean.TRUE : req.active())
                .build());
    }
}
