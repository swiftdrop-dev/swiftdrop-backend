package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.dto.response.ParcelAssignmentHistoryResponse;
import com.swiftdrop.parcel.dto.response.ParcelDeliveryResponse;
import com.swiftdrop.parcel.dto.response.ParcelPageResponse;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.exception.InvalidRequestException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ParcelQueryService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");

    private final ParcelRepository parcelRepository;
    private final ParcelAssignmentRepository assignmentRepository;

    public ParcelQueryService(ParcelRepository parcelRepository,
                              ParcelAssignmentRepository assignmentRepository) {
        this.parcelRepository = parcelRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public ParcelPageResponse<ParcelDeliveryResponse> findAssignedOn(String courierId, LocalDate date,
                                                                     int page, int size) {
        var pageable = PageRequest.of(page, size);
        var startInclusive = date.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant endExclusive;
        try {
            endExclusive = date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        } catch (DateTimeException exception) {
            throw new InvalidRequestException("assignedDate must allow a following calendar day");
        }
        var assignmentPage = assignmentRepository
                .findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanAndUnassignedAtIsNullOrderByAssignedAtAscIdAsc(
                        courierId, startInclusive, endExclusive, pageable);
        var assignments = assignmentPage.getContent();
        if (assignments.isEmpty()) {
            return new ParcelPageResponse<>(List.of(), page, size, assignmentPage.hasNext());
        }

        var parcelIds = assignments.stream().map(assignment -> assignment.getParcelId()).toList();
        Map<String, Parcel> parcelsById = parcelRepository.findByIdInAndCourierId(parcelIds, courierId).stream()
                .collect(Collectors.toMap(Parcel::getId, Function.identity()));
        List<ParcelDeliveryResponse> content = assignments.stream()
                .map(assignment -> parcelsById.get(assignment.getParcelId()))
                .filter(java.util.Objects::nonNull)
                .map(this::toDeliveryResponse)
                .toList();
        return new ParcelPageResponse<>(content, page, size, assignmentPage.hasNext());
    }

    public ParcelPageResponse<ParcelDeliveryResponse> findScheduledFor(String courierId, LocalDate date,
                                                                       int page, int size) {
        var parcelPage = parcelRepository
                .findByCourierIdAndEstimatedDeliveryDateOrderByTrackingNumberAsc(
                        courierId, date.toString(), PageRequest.of(page, size));
        return new ParcelPageResponse<>(parcelPage.map(this::toDeliveryResponse).getContent(),
                page, size, parcelPage.hasNext());
    }

    public java.util.List<ParcelAssignmentHistoryResponse> getAssignmentHistory(String parcelId) {
        if (!parcelRepository.existsById(parcelId)) {
            throw new ParcelNotFoundException(parcelId);
        }
        return assignmentRepository.findByParcelIdOrderByAssignedAtAscIdAsc(parcelId).stream()
                .map(assignment -> new ParcelAssignmentHistoryResponse(
                        assignment.getId(), assignment.getCourierId(), assignment.getAssignedAt(),
                        assignment.getUnassignedAt(), assignment.getChangedBy(), assignment.getReason()))
                .toList();
    }

    private ParcelDeliveryResponse toDeliveryResponse(Parcel parcel) {
        return new ParcelDeliveryResponse(parcel.getTrackingNumber(), parcel.getStatus(),
                parcel.getEstimatedDeliveryDate(), parcel.getRecipientName(),
                parcel.getRecipientPhone(), parcel.getRecipientAddress());
    }
}
