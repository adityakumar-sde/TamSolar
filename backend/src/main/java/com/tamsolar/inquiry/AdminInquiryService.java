package com.tamsolar.inquiry;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminInquiryService {

    private final AdminInquiryRepository adminRepository;
    private final InquiryRepository inquiryRepository;
    private final EmailOutboxRepository outboxRepository;
    private final EmailOutboxService emailOutboxService;

    public AdminInquiryService(
        AdminInquiryRepository adminRepository,
        InquiryRepository inquiryRepository,
        EmailOutboxRepository outboxRepository,
        EmailOutboxService emailOutboxService
    ) {
        this.adminRepository = adminRepository;
        this.inquiryRepository = inquiryRepository;
        this.outboxRepository = outboxRepository;
        this.emailOutboxService = emailOutboxService;
    }

    public PageResult list(
        String search,
        String status,
        LocalDate fromDate,
        LocalDate toDate,
        int page,
        int size
    ) {
        if (search != null && search.length() > 100) {
            throw new IllegalArgumentException("Search must be 100 characters or fewer.");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Start date must be on or before end date.");
        }
        if (status != null && !status.isBlank()
            && !java.util.Set.of("NEW", "IN_PROGRESS", "REPLIED", "CONVERTED", "CLOSED").contains(status)) {
            throw new IllegalArgumentException("Choose a valid enquiry status.");
        }
        int boundedPage = Math.min(Math.max(0, page), 1_000_000);
        int boundedSize = Math.max(1, Math.min(size, 100));
        return adminRepository.list(search, status, fromDate, toDate, boundedPage, boundedSize);
    }

    public Map<String, Object> detail(long inquiryId) {
        Map<String, Object> inquiry = inquiryRepository.getForAdmin(inquiryId)
            .orElseThrow(() -> new InquiryNotFoundException(inquiryId));
        Map<String, Object> result = new LinkedHashMap<>(inquiry);
        result.put("conversation", adminRepository.conversation(inquiryId));
        result.put("emailDeliveries", outboxRepository.getDeliveryStatuses(inquiryId));
        return result;
    }

    @Transactional
    public void updateStatus(long inquiryId, String status) {
        requireInquiry(inquiryId);
        adminRepository.updateStatus(inquiryId, status);
    }

    @Transactional
    public void updateNotes(long inquiryId, String notes) {
        requireInquiry(inquiryId);
        adminRepository.updateNotes(inquiryId, notes);
    }

    @Transactional
    public long reply(long inquiryId, String subject, String message, String idempotencyKey) {
        requireInquiry(inquiryId);
        return emailOutboxService.queueAdminReply(inquiryId, subject, message, idempotencyKey);
    }

    private void requireInquiry(long inquiryId) {
        if (inquiryRepository.getForAdmin(inquiryId).isEmpty()) {
            throw new InquiryNotFoundException(inquiryId);
        }
    }
}
