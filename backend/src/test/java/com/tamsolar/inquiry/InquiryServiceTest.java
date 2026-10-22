package com.tamsolar.inquiry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;

    @Mock
    private EmailOutboxService emailOutboxService;

    @Test
    void submissionRetryReturnsOriginalEnquiryAndDoesNotQueueDuplicateEmails() {
        when(inquiryRepository.findBySubmissionKey("stable-key"))
            .thenReturn(Optional.<Long>empty(), Optional.of(81L));
        when(inquiryRepository.create(
            "Customer", "customer@example.com", "Homeowner", "Solar quote", "Interested."
        )).thenReturn(81L);
        InquiryRequest request = new InquiryRequest();
        request.setName(" Customer ");
        request.setEmail(" customer@example.com ");
        request.setCustomerType("Homeowner");
        request.setEnquiryType("Solar quote");
        request.setMessage(" Interested. ");
        InquiryService service = new InquiryService(inquiryRepository, emailOutboxService);

        assertEquals(81L, service.createInquiry(request, "stable-key"));
        assertEquals(81L, service.createInquiry(request, "stable-key"));

        verify(inquiryRepository, times(1)).create(
            "Customer", "customer@example.com", "Homeowner", "Solar quote", "Interested."
        );
        verify(inquiryRepository, times(1)).saveSubmissionKey("stable-key", 81L);
        verify(inquiryRepository, times(1)).initializeWorkflow(81L);
        verify(emailOutboxService, times(1)).queueInitialMessages(81L, request);
        verify(inquiryRepository, never()).deleteInquiry(81L);
    }
}
