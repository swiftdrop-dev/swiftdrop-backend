package com.swiftdrop.dispatch.controller;

import com.swiftdrop.dispatch.dto.ApiResponse;
import com.swiftdrop.dispatch.dto.request.UpdateCourierStatusProjectionRequest;
import com.swiftdrop.dispatch.service.CourierStatusProjectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dispatch/projections/couriers")
public class CourierStatusProjectionController {

    private final CourierStatusProjectionService projectionService;

    public CourierStatusProjectionController(CourierStatusProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Void>> syncCourierStatus(
            @Valid @RequestBody UpdateCourierStatusProjectionRequest request) {
        projectionService.projectCourierStatus(request);
        return ResponseEntity.ok(ApiResponse.success("Courier projection updated", null));
    }
}
