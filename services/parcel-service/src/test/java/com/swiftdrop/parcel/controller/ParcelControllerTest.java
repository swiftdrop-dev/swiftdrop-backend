package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.dto.response.ParcelAssignmentHistoryResponse;
import com.swiftdrop.parcel.dto.response.ParcelPageResponse;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.exception.AssignmentConflictException;
import com.swiftdrop.parcel.exception.InvalidRequestException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.service.AssignmentOutcome;
import com.swiftdrop.parcel.service.ParcelAssignmentService;
import com.swiftdrop.parcel.service.ParcelQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParcelController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.swiftdrop.parcel.exception.ParcelRestExceptionHandler.class)
class ParcelControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ParcelAssignmentService assignmentService;

    @MockBean
    private ParcelQueryService parcelQueryService;

    @Test
    void createsNewAssignmentAndDoesNotAcceptCallerChangedBy() throws Exception {
        ParcelAssignment assignment = new ParcelAssignment("prc_001", "cou_001",
                Instant.parse("2026-11-14T06:25:00Z"), null, "Assignment");
        when(assignmentService.assign("prc_001", "cou_001", null, "Assignment"))
                .thenReturn(new AssignmentOutcome(assignment, true));

        mockMvc.perform(post("/internal/v1/parcels/prc_001/assignments")
                        .contentType("application/json")
                        .content("{\"courierId\":\"cou_001\",\"reason\":\"Assignment\",\"changedBy\":\"spoofed\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelId").value("prc_001"))
                .andExpect(jsonPath("$.courierId").value("cou_001"))
                .andExpect(jsonPath("$.unassignedAt").isEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist("Location"));

        verify(assignmentService).assign("prc_001", "cou_001", null, "Assignment");
    }

    @Test
    void returns200ForIdempotentAssignment() throws Exception {
        ParcelAssignment assignment = new ParcelAssignment("prc_001", "cou_001",
                Instant.parse("2026-11-14T06:25:00Z"), null, null);
        when(assignmentService.assign("prc_001", "cou_001", null, null))
                .thenReturn(new AssignmentOutcome(assignment, false));

        mockMvc.perform(post("/internal/v1/parcels/prc_001/assignments")
                        .contentType("application/json")
                        .content("{\"courierId\":\"cou_001\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void returns400ForInvalidAssignmentRequest() throws Exception {
        mockMvc.perform(post("/internal/v1/parcels/prc_001/assignments")
                        .contentType("application/json")
                        .content("{\"courierId\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void rejectsReasonLongerThanRequestLimit() throws Exception {
        String reason = "r".repeat(1001);

        mockMvc.perform(post("/internal/v1/parcels/prc_001/assignments")
                        .contentType("application/json")
                        .content("{\"courierId\":\"cou_001\",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void acceptsReasonAtRequestLimit() throws Exception {
        String reason = "r".repeat(1000);
        ParcelAssignment assignment = new ParcelAssignment("prc_001", "cou_001",
                Instant.parse("2026-11-14T06:25:00Z"), null, reason);
        when(assignmentService.assign("prc_001", "cou_001", null, reason))
                .thenReturn(new AssignmentOutcome(assignment, true));

        mockMvc.perform(post("/internal/v1/parcels/prc_001/assignments")
                        .contentType("application/json")
                        .content("{\"courierId\":\"cou_001\",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isCreated());

        verify(assignmentService).assign("prc_001", "cou_001", null, reason);
    }

    @Test
    void returns404ForMissingParcelAnd409ForAssignmentConflict() throws Exception {
        when(assignmentService.assign("missing", "cou_001", null, null))
                .thenThrow(new ParcelNotFoundException("missing"));
        when(assignmentService.assign("prc_001", "cou_001", null, null))
                .thenThrow(new AssignmentConflictException("prc_001", new RuntimeException("duplicate")));

        mockMvc.perform(post("/internal/v1/parcels/missing/assignments")
                        .contentType("application/json").content("{\"courierId\":\"cou_001\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(post("/internal/v1/parcels/prc_001/assignments")
                        .contentType("application/json").content("{\"courierId\":\"cou_001\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void returnsEmptyPageEnvelopeAndUsesDefaultPaginationForAssignedDate() throws Exception {
        when(parcelQueryService.findAssignedOn("cou_001", LocalDate.parse("2026-11-14"), 0, 50))
                .thenReturn(new ParcelPageResponse<>(List.of(), 0, 50, false));

        mockMvc.perform(get("/internal/v1/parcels")
                        .param("courierId", "cou_001")
                        .param("assignedDate", "2026-11-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(parcelQueryService).findAssignedOn("cou_001", LocalDate.parse("2026-11-14"), 0, 50);
    }

    @Test
    void rejectsMissingBothDateModesAndMalformedDates() throws Exception {
        mockMvc.perform(get("/internal/v1/parcels").param("courierId", "cou_001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/internal/v1/parcels")
                        .param("courierId", "cou_001")
                        .param("assignedDate", "2026-11-14")
                        .param("scheduledDate", "2026-11-14"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/internal/v1/parcels")
                        .param("courierId", "cou_001")
                        .param("scheduledDate", "2026-02-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void allowsPaginationThroughMaximumAndRejectsLargerPageSize() throws Exception {
        when(parcelQueryService.findScheduledFor("cou_001", LocalDate.parse("2026-11-14"), 2, 100))
                .thenReturn(new ParcelPageResponse<>(List.of(), 2, 100, true));
        mockMvc.perform(get("/internal/v1/parcels")
                        .param("courierId", "cou_001")
                        .param("scheduledDate", "2026-11-14")
                        .param("page", "2")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.hasNext").value(true));
        verify(parcelQueryService).findScheduledFor("cou_001", LocalDate.parse("2026-11-14"), 2, 100);

        mockMvc.perform(get("/internal/v1/parcels")
                        .param("courierId", "cou_001")
                        .param("scheduledDate", "2026-11-14")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenAssignedDateHasNoFollowingCalendarDay() throws Exception {
        when(parcelQueryService.findAssignedOn("cou_001", LocalDate.MAX, 0, 50))
                .thenThrow(new InvalidRequestException("assignedDate must allow a following calendar day"));

        mockMvc.perform(get("/internal/v1/parcels")
                        .param("courierId", "cou_001")
                        .param("assignedDate", LocalDate.MAX.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void assignmentHistoryReturnsOrderedRowsAndUnknownParcelIs404() throws Exception {
        when(parcelQueryService.getAssignmentHistory("prc_001")).thenReturn(List.of(
                new ParcelAssignmentHistoryResponse(1L, "cou_old", Instant.parse("2026-11-13T00:00:00Z"),
                        Instant.parse("2026-11-14T00:00:00Z"), null, "Reassigned"),
                new ParcelAssignmentHistoryResponse(2L, "cou_new", Instant.parse("2026-11-14T00:00:00Z"),
                        null, null, "Reassigned")));
        when(parcelQueryService.getAssignmentHistory("missing")).thenThrow(new ParcelNotFoundException("missing"));

        mockMvc.perform(get("/internal/v1/parcels/prc_001/assignment-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courierId").value("cou_old"))
                .andExpect(jsonPath("$[1].courierId").value("cou_new"));
        mockMvc.perform(get("/internal/v1/parcels/missing/assignment-history"))
                .andExpect(status().isNotFound());
    }
}
