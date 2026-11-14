package com.swiftdrop.parcel.customer;

import static com.swiftdrop.parcel.customer.BookingContracts.*;
import static com.swiftdrop.parcel.customer.ParcelStatus.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;

@Service
public class CustomerBookingService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    private static final Set<String> DISTRICTS = Set.of("Colombo", "Gampaha", "Kalutara", "Kandy", "Matale", "Nuwara Eliya", "Galle", "Matara", "Hambantota", "Jaffna", "Kilinochchi", "Mannar", "Vavuniya", "Mullaitivu", "Batticaloa", "Ampara", "Trincomalee", "Kurunegala", "Puttalam", "Anuradhapura", "Polonnaruwa", "Badulla", "Monaragala", "Ratnapura", "Kegalle");
    private static final Set<ParcelStatus> IN_PROGRESS = Set.of(ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY);
    private final CustomerParcelRepository repository;
    private final PricingPort pricing;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CustomerBookingService(CustomerParcelRepository repository, PricingPort pricing) {
        this(repository, pricing, Clock.systemUTC());
    }
    CustomerBookingService(CustomerParcelRepository repository, PricingPort pricing, Clock clock) {
        this.repository = repository; this.pricing = pricing; this.clock = clock;
    }

    public record Customer(String id, String name, String phone) {}

    public PricingPort.Quote estimate(CreateBooking request) {
        if (!DISTRICTS.contains(request.pickupAddress().district().trim()) || !DISTRICTS.contains(request.recipientAddress().district().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid Sri Lankan district");
        }
        return pricing.quote(request);
    }

    @Transactional
    public ParcelView create(Customer customer, CreateBooking request) {
        PricingPort.Quote quote = estimate(request);
        Instant now = clock.instant();
        CustomerParcel parcel = new CustomerParcel();
        parcel.id = UUID.randomUUID().toString();
        parcel.trackingNumber = "SWF-" + now.atZone(ZONE).getYear() + "-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        parcel.status = CREATED;
        parcel.senderId = customer.id();
        parcel.senderName = customer.name();
        parcel.senderPhone = customer.phone();
        parcel.recipientName = request.recipientName().trim();
        parcel.recipientPhone = request.recipientPhone();
        parcel.pickupAddress = new StoredAddress(request.pickupAddress());
        parcel.recipientAddress = new StoredAddress(request.recipientAddress());
        parcel.description = request.description().trim();
        parcel.weight = request.weight(); parcel.size = request.size();
        parcel.declaredValue = request.declaredValue(); parcel.fragile = request.fragile();
        parcel.specialInstructions = request.specialInstructions() == null ? null : request.specialInstructions().trim();
        parcel.totalFee = quote.totalFee(); parcel.paymentMethod = request.paymentMethod();
        // Real payment gateways are outside the project scope. Booking never proves payment.
        parcel.paymentStatus = "pending";
        parcel.createdAt = now;
        parcel.estimatedDeliveryDate = now.atZone(ZONE).toLocalDate().plusDays(quote.estimatedDays());
        parcel.history.add(new CustomerParcel.HistoryEntry(CREATED, now, "Booking confirmed"));
        return view(repository.save(parcel));
    }

    @Transactional(readOnly = true)
    public ParcelView get(String customerId, String id) { return view(owned(customerId, id)); }

    @Transactional(readOnly = true)
    public ParcelPage list(String customerId, String search, String status, LocalDate from, LocalDate to, String sort, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not follow to date");
        Set<ParcelStatus> statuses;
        try {
            statuses = status.equals("all") ? Set.of() : status.equals("in_progress") ? IN_PROGRESS : Set.of(ParcelStatus.fromFilter(status));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status filter");
        }
        Specification<CustomerParcel> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("senderId"), customerId));
            if (!statuses.isEmpty()) predicates.add(root.get("status").in(statuses));
            if (!search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                predicates.add(builder.or(builder.like(builder.lower(root.get("trackingNumber")), pattern, '!'), builder.like(builder.lower(root.get("recipientName")), pattern, '!')));
            }
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay(ZONE).toInstant()));
            if (to != null) predicates.add(builder.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay(ZONE).toInstant()));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Sort ordering = Sort.by(sort.equals("oldest") ? Sort.Direction.ASC : Sort.Direction.DESC, "createdAt", "id");
        Page<CustomerParcel> result = repository.findAll(specification, PageRequest.of(page, size, ordering));
        return new ParcelPage(result.getContent().stream().map(this::view).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(String customerId) {
        long inProgress = IN_PROGRESS.stream().mapToLong(status -> repository.countBySenderIdAndStatus(customerId, status)).sum();
        return new Dashboard(repository.countBySenderId(customerId), repository.countBySenderIdAndStatus(customerId, CREATED), inProgress,
                repository.countBySenderIdAndStatus(customerId, DELIVERED), repository.countBySenderIdAndStatus(customerId, FAILED),
                repository.countBySenderIdAndStatus(customerId, CANCELLED), repository.countBySenderIdAndStatus(customerId, RETURNED));
    }

    @Transactional
    public ParcelView feedback(String customerId, String id, Feedback request) {
        CustomerParcel parcel = owned(customerId, id);
        if (parcel.status != DELIVERED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Feedback is available only after delivery");
        parcel.rating = request.rating();
        parcel.feedbackComment = request.comment() == null ? null : request.comment().trim();
        parcel.feedbackSubmittedAt = clock.instant();
        return view(repository.save(parcel));
    }

    private CustomerParcel owned(String customerId, String id) {
        return repository.findByIdAndSenderId(id, customerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));
    }

    private ParcelView view(CustomerParcel p) {
        return new ParcelView(p.id, p.trackingNumber, p.status.uiValue(), p.senderId, p.senderName, p.senderPhone, "customer",
                p.recipientName, p.recipientPhone, p.pickupAddress.view(), p.recipientAddress.view(), p.description, p.weight,
                p.size, p.declaredValue, p.fragile, p.specialInstructions, p.totalFee, p.totalFee, p.paymentStatus, p.paymentMethod,
                p.createdAt, p.estimatedDeliveryDate, p.history.stream().map(h -> new StatusEvent(h.status.uiValue(), h.timestamp, h.note)).toList(),
                p.rating, p.feedbackComment, p.feedbackSubmittedAt);
    }
}
