package com.tamsolar.inquiry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InquiryController.class)
class InquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InquiryService inquiryService;

    @MockitoBean
    private InquiryRateLimiter rateLimiter;

    @org.junit.jupiter.api.BeforeEach
    void allowEnquiryRequestsInTests() {
        when(rateLimiter.allow(any())).thenReturn(true);
    }

    @Test
    void savesValidEnquiryAndReturnsReference() throws Exception {
        when(inquiryService.createInquiry(any(InquiryRequest.class))).thenReturn(42L);

        mockMvc.perform(post("/api/inquiries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Test Customer",
                      "email": "customer@example.com",
                      "customerType": "Homeowner",
                      "enquiryType": "Solar quote",
                      "message": "I would like information about a solar quote."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reference").value("TS-000042"));

        verify(inquiryService).createInquiry(any(InquiryRequest.class));
    }

    @Test
    void reusesReferenceWhenSubmissionIsRetriedWithSameKey() throws Exception {
        when(inquiryService.createInquiry(any(InquiryRequest.class), eq("submission-1"))).thenReturn(42L);

        mockMvc.perform(post("/api/inquiries")
                .header("Idempotency-Key", "submission-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Test Customer",
                      "email": "customer@example.com",
                      "customerType": "Homeowner",
                      "enquiryType": "Solar quote",
                      "message": "I would like information about a solar quote."
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reference").value("TS-000042"));

        verify(inquiryService).createInquiry(any(InquiryRequest.class), eq("submission-1"));
    }

    @Test
    void rejectsInvalidEnquiryWithoutSavingIt() throws Exception {
        mockMvc.perform(post("/api/inquiries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "",
                      "email": "not-an-email",
                      "customerType": "Visitor",
                      "enquiryType": "Unlisted topic",
                      "message": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void healthEndpointChecksDatabaseConnection() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.database").value("TamSolar"));

        verify(inquiryService).checkDatabaseConnection();
    }

    @Test
    void databaseFailureReturnsServiceUnavailable() throws Exception {
        doThrow(new DataAccessResourceFailureException("database unavailable"))
            .when(inquiryService).checkDatabaseConnection();

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value(
                "We couldn’t save your enquiry right now. Please try again or contact TAM Solar directly."
            ));
    }

    @Test
    void servesTheWebsiteFromSpringBoot() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("index.html"));
    }
}
