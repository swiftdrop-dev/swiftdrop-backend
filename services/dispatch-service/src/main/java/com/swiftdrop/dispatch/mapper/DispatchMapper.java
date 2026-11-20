package com.swiftdrop.dispatch.mapper;

import com.swiftdrop.dispatch.dto.response.DispatchAssignmentResponse;
import com.swiftdrop.dispatch.dto.response.DispatchJobResponse;
import com.swiftdrop.dispatch.dto.response.DispatchJobSummaryResponse;
import com.swiftdrop.dispatch.dto.response.DispatchZoneResponse;
import com.swiftdrop.dispatch.entity.DispatchAssignment;
import com.swiftdrop.dispatch.entity.DispatchJob;
import com.swiftdrop.dispatch.entity.DispatchZone;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class DispatchMapper {

    public DispatchJobResponse toJobResponse(DispatchJob job) {
        if (job == null) {
            return null;
        }

        DispatchJobResponse response = new DispatchJobResponse();
        response.setId(job.getId());
        response.setParcelId(job.getParcelId());
        response.setTrackingNumber(job.getTrackingNumber());
        response.setMerchantId(job.getMerchantId());
        if (job.getZone() != null) {
            response.setZoneCode(job.getZone().getZoneCode());
        }
        response.setCourierId(job.getCourierId());
        response.setPickupStreetAddress(job.getPickupStreetAddress());
        response.setPickupCity(job.getPickupCity());
        response.setPickupLatitude(job.getPickupLatitude());
        response.setPickupLongitude(job.getPickupLongitude());
        response.setDeliveryStreetAddress(job.getDeliveryStreetAddress());
        response.setDeliveryCity(job.getDeliveryCity());
        response.setDeliveryLatitude(job.getDeliveryLatitude());
        response.setDeliveryLongitude(job.getDeliveryLongitude());
        response.setParcelWeightKg(job.getParcelWeightKg());
        response.setRequiredVehicleType(job.getRequiredVehicleType());
        response.setPriority(job.getPriority());
        response.setDispatchType(job.getDispatchType());
        response.setStatus(job.getStatus());
        response.setAssignedBy(job.getAssignedBy());
        response.setAssignedAt(job.getAssignedAt());
        response.setOfferExpiresAt(job.getOfferExpiresAt());
        response.setAcceptedAt(job.getAcceptedAt());
        response.setCompletedAt(job.getCompletedAt());
        response.setCancelledAt(job.getCancelledAt());
        response.setCancellationReason(job.getCancellationReason());
        response.setDispatcherNotes(job.getDispatcherNotes());
        response.setCreatedAt(job.getCreatedAt());
        response.setUpdatedAt(job.getUpdatedAt());

        if (job.getAssignments() != null) {
            response.setAssignments(job.getAssignments().stream()
                    .map(this::toAssignmentResponse)
                    .collect(Collectors.toList()));
        }

        return response;
    }

    public DispatchJobSummaryResponse toJobSummaryResponse(DispatchJob job) {
        if (job == null) {
            return null;
        }

        DispatchJobSummaryResponse response = new DispatchJobSummaryResponse();
        response.setId(job.getId());
        response.setParcelId(job.getParcelId());
        response.setTrackingNumber(job.getTrackingNumber());
        response.setMerchantId(job.getMerchantId());
        if (job.getZone() != null) {
            response.setZoneCode(job.getZone().getZoneCode());
        }
        response.setCourierId(job.getCourierId());
        response.setPickupCity(job.getPickupCity());
        response.setDeliveryCity(job.getDeliveryCity());
        response.setParcelWeightKg(job.getParcelWeightKg());
        response.setRequiredVehicleType(job.getRequiredVehicleType());
        response.setPriority(job.getPriority());
        response.setDispatchType(job.getDispatchType());
        response.setStatus(job.getStatus());
        response.setCreatedAt(job.getCreatedAt());

        return response;
    }

    public DispatchAssignmentResponse toAssignmentResponse(DispatchAssignment assignment) {
        if (assignment == null) {
            return null;
        }

        DispatchAssignmentResponse response = new DispatchAssignmentResponse();
        response.setId(assignment.getId());
        if (assignment.getDispatchJob() != null) {
            response.setDispatchJobId(assignment.getDispatchJob().getId());
        }
        response.setCourierId(assignment.getCourierId());
        response.setAttemptNumber(assignment.getAttemptNumber());
        response.setDecision(assignment.getDecision());
        response.setDecisionReason(assignment.getDecisionReason());
        response.setOfferedDistanceKm(assignment.getOfferedDistanceKm());
        response.setOfferedAt(assignment.getOfferedAt());
        response.setRespondedAt(assignment.getRespondedAt());

        return response;
    }

    public DispatchZoneResponse toZoneResponse(DispatchZone zone) {
        if (zone == null) {
            return null;
        }

        DispatchZoneResponse response = new DispatchZoneResponse();
        response.setId(zone.getId());
        response.setZoneCode(zone.getZoneCode());
        response.setZoneName(zone.getZoneName());
        response.setDescription(zone.getDescription());
        response.setCenterLatitude(zone.getCenterLatitude());
        response.setCenterLongitude(zone.getCenterLongitude());
        response.setRadiusKm(zone.getRadiusKm());
        response.setAssignedDispatcherId(zone.getAssignedDispatcherId());
        response.setIsActive(zone.getIsActive());
        response.setCreatedAt(zone.getCreatedAt());

        return response;
    }
}
