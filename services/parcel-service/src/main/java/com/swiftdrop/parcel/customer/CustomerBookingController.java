package com.swiftdrop.parcel.customer;

import static com.swiftdrop.parcel.customer.BookingContracts.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/customer")
public class CustomerBookingController {
    private final CustomerBookingService service;
    public CustomerBookingController(CustomerBookingService service) { this.service = service; }

    private CustomerBookingService.Customer customer(Jwt jwt) {
        String id = jwt.getClaimAsString("userId");
        if (id == null || id.isBlank() || id.length() > 100) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid customer identity is required");
        String name = jwt.getClaimAsString("name");
        String phone = jwt.getClaimAsString("phone");
        // Optional display claims do not affect ownership; bound them before persistence.
        return new CustomerBookingService.Customer(id, name != null && name.length() <= 100 ? name : "", phone != null && phone.length() <= 32 ? phone : "");
    }

    @PostMapping("/parcels/estimate")
    public PricingPort.Quote estimate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBooking request) {
        customer(jwt);
        return service.estimate(request);
    }

    @PostMapping("/parcels")
    public ResponseEntity<ParcelView> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBooking request) {
        ParcelView parcel = service.create(customer(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/customer/parcels/" + parcel.id())).body(parcel);
    }

    @GetMapping("/parcels")
    public ParcelPage list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "") @Size(max = 100) String search,
            @RequestParam(defaultValue = "all") @Size(max = 32) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "newest") @Pattern(regexp = "newest|oldest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(100) int size) {
        return service.list(customer(jwt).id(), search, status, fromDate, toDate, sort, page, size);
    }

    @GetMapping("/parcels/{id}")
    public ParcelView get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) { return service.get(customer(jwt).id(), id); }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@AuthenticationPrincipal Jwt jwt) { return service.dashboard(customer(jwt).id()); }

    @PutMapping("/parcels/{id}/feedback")
    public ParcelView feedback(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody Feedback request) {
        return service.feedback(customer(jwt).id(), id, request);
    }
}
