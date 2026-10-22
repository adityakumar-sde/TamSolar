
package com.tamsolar.inquiry;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inquiries")
public class AdminInquiryController {

    private final AdminInquiryService adminService;

    public AdminInquiryController(AdminInquiryService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public PageResult list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.list(
                search, status, fromDate, toDate, page, size);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        return adminService.detail(id);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable long id,
            @Valid @RequestBody AdminStatusRequest request) {
        adminService.updateStatus(id, request.getStatus());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/notes")
    public ResponseEntity<Void> updateNotes(
            @PathVariable long id,
            @Valid @RequestBody AdminNotesRequest request) {
        adminService.updateNotes(id, request.getNotes());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<Map<String, Object>> reply(
            @PathVariable long id,
            @Valid @RequestBody AdminReplyRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must contain 1 to 100 characters.");
        }

        long outboxId = adminService.reply(
                id,
                request.getSubject(),
                request.getMessage(),
                idempotencyKey);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "replyId", outboxId,
                "deliveryStatus", "PENDING"));
    }
}
