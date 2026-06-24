package com.booking.controller;

import com.booking.dto.Responses;
import com.booking.entity.Show;
import com.booking.entity.ShowSeat;
import com.booking.repository.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CityRepository cityRepository;
    private final TheaterRepository theaterRepository;
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public CatalogController(CityRepository cityRepository, TheaterRepository theaterRepository,
                             MovieRepository movieRepository, ShowRepository showRepository,
                             ShowSeatRepository showSeatRepository) {
        this.cityRepository = cityRepository;
        this.theaterRepository = theaterRepository;
        this.movieRepository = movieRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    @GetMapping("/cities")
    public List<Responses.CityResponse> cities() {
        return cityRepository.findAll().stream().map(Responses.CityResponse::from).toList();
    }

    @GetMapping("/cities/{cityId}/theaters")
    public List<Responses.TheaterResponse> theaters(@PathVariable Long cityId) {
        return theaterRepository.findByCityId(cityId).stream().map(Responses.TheaterResponse::from).toList();
    }

    @GetMapping("/movies")
    public List<Responses.MovieResponse> movies() {
        return movieRepository.findAll().stream().map(Responses.MovieResponse::from).toList();
    }

    @GetMapping("/shows")
    public List<Responses.ShowResponse> shows(@RequestParam(required = false) Long cityId,
                                              @RequestParam(required = false) Long movieId,
                                              @RequestParam(required = false) String date) {
        LocalDateTime from = null, to = null;
        if (date != null && !date.isBlank()) {
            LocalDate d = LocalDate.parse(date);
            from = d.atStartOfDay();
            to = d.plusDays(1).atStartOfDay();
        }
        List<Show> shows = showRepository.search(cityId, movieId, from, to);
        return shows.stream().map(Responses.ShowResponse::from).toList();
    }

    @GetMapping("/shows/{showId}")
    public Responses.ShowResponse showDetail(@PathVariable Long showId) {
        Show s = showRepository.findById(showId).orElseThrow();
        return Responses.ShowResponse.from(s);
    }

    @GetMapping("/shows/{showId}/seats")
    public List<Responses.ShowSeatResponse> seatsForShow(@PathVariable Long showId) {
        List<ShowSeat> seats = showSeatRepository.findByShowId(showId);
        return seats.stream().map(Responses.ShowSeatResponse::from).toList();
    }
}
