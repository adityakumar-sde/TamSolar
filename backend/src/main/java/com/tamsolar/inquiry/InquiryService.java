package com.tamsolar.inquiry;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

@Service
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final EmailOutboxService emailOutboxService;

    public InquiryService(InquiryRepository inquiryRepository, EmailOutboxService emailOutboxService) {
        this.inquiryRepository = inquiryRepository;
        this.emailOutboxService = emailOutboxService;
    }

    @Transactional
    public Long createInquiry(InquiryRequest request) {
        return createInquiry(request, null);
    }

    @Transactional
    public Long createInquiry(InquiryRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = inquiryRepository.findBySubmissionKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        Long id = inquiryRepository.create(
            request.getName().trim(),
            request.getEmail().trim(),
            request.getPhone() != null ? request.getPhone().trim() : null,
            request.getCustomerType(),
            request.getEnquiryType(),
            request.getMessage().trim()
        );
        if (idempotencyKey != null) {
            try {
                inquiryRepository.saveSubmissionKey(idempotencyKey, id);
            } catch (DuplicateKeyException duplicate) {
                Long existing = inquiryRepository.findBySubmissionKey(idempotencyKey)
                    .orElseThrow(() -> duplicate);
                inquiryRepository.deleteInquiry(id);
                return existing;
            }
        }
        inquiryRepository.initializeWorkflow(id);
        emailOutboxService.queueInitialMessages(id, request);
        return id;
    }

    public void checkDatabaseConnection() {
        inquiryRepository.checkConnection();
    }

    public List<String> getTableNames() {
        return inquiryRepository.getTableNames();
    }
}
