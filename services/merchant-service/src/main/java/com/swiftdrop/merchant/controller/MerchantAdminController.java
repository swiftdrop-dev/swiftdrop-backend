package com.swiftdrop.merchant.controller;

import com.swiftdrop.merchant.dto.ApiResponse;
import com.swiftdrop.merchant.dto.request.MerchantApprovalRequest;
import com.swiftdrop.merchant.dto.response.MerchantAuditLogResponse;
import com.swiftdrop.merchant.dto.response.MerchantDocumentResponse;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.dto.response.MerchantSummaryResponse;
import com.swiftdrop.merchant.entity.enums.MerchantStatus;
import com.swiftdrop.merchant.security.SecurityUtils;
import com.swiftdrop.merchant.service.MerchantAdminService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchants/admin")
public class MerchantAdminController {

    private final MerchantAdminService adminService;

    public MerchantAdminController(MerchantAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<Page<MerchantSummaryResponse>>> getPending(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<MerchantSummaryResponse> page = adminService.getPendingMerchants(pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<MerchantSummaryResponse>>> getAll(
            @RequestParam(value = "status", required = false) MerchantStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<MerchantSummaryResponse> page = adminService.getAllMerchants(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<MerchantResponse>> approve(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) MerchantApprovalRequest request) {
        UUID adminId = SecurityUtils.getCurrentUserId()
                .orElse(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        MerchantResponse response = adminService.approveMerchant(
                id,
                request != null ? request.getPricingTier() : null,
                adminId
        );
        return ResponseEntity.ok(ApiResponse.success("Merchant approved successfully", response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<MerchantResponse>> reject(
            @PathVariable("id") UUID id,
            @RequestBody MerchantApprovalRequest request) {
        UUID adminId = SecurityUtils.getCurrentUserId()
                .orElse(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        String reason = request != null ? request.getRejectionReason() : "Application rejected by admin";
        MerchantResponse response = adminService.rejectMerchant(id, reason, adminId);
        return ResponseEntity.ok(ApiResponse.success("Merchant application rejected", response));
    }

    @PostMapping("/{id}/documents/{docId}/review")
    public ResponseEntity<ApiResponse<MerchantDocumentResponse>> reviewDocument(
            @PathVariable("id") UUID id,
            @PathVariable("docId") UUID docId,
            @RequestParam("approve") boolean approve,
            @RequestParam(value = "reason", required = false) String reason) {
        UUID adminId = SecurityUtils.getCurrentUserId()
                .orElse(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        MerchantDocumentResponse response = adminService.reviewDocument(id, docId, approve, reason, adminId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<ApiResponse<MerchantResponse>> suspend(
            @PathVariable("id") UUID id,
            @RequestParam(value = "reason", required = false) String reason) {
        UUID adminId = SecurityUtils.getCurrentUserId()
                .orElse(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        MerchantResponse response = adminService.suspendMerchant(id, reason, adminId);
        return ResponseEntity.ok(ApiResponse.success("Merchant suspended", response));
    }

    @GetMapping("/{id}/audit-logs")
    public ResponseEntity<ApiResponse<List<MerchantAuditLogResponse>>> getAuditLogs(
            @PathVariable("id") UUID id) {
        List<MerchantAuditLogResponse> logs = adminService.getAuditLogs(id);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
