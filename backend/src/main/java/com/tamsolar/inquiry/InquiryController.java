package com.tamsolar.inquiry;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class InquiryController {

    private final InquiryService inquiryService;
    private final InquiryRateLimiter rateLimiter;

    public InquiryController(InquiryService inquiryService, InquiryRateLimiter rateLimiter) {
        this.inquiryService = inquiryService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/inquiries")
    public ResponseEntity<Map<String, String>> createInquiry(
        @Valid @RequestBody InquiryRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest httpRequest
    ) {
        if (!rateLimiter.allow(httpRequest.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", "Too many enquiries were submitted from this connection. Please try again later."
            ));
        }
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 100)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provide a valid submission key."));
        }
        Long id = idempotencyKey == null
            ? inquiryService.createInquiry(request)
            : inquiryService.createInquiry(request, idempotencyKey);
        String reference = "TS-%06d".formatted(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("reference", reference));
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        inquiryService.checkDatabaseConnection();
        return Map.of("status", "ok", "database", "TamSolar");
    }
}
