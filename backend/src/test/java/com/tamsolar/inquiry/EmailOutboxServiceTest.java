package com.tamsolar.inquiry;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import jakarta.mail.Address;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailOutboxServiceTest {

    @Mock
    private EmailOutboxRepository outboxRepository;

    @Mock
    private InquiryRepository inquiryRepository;

    @Mock
    private JavaMailSender mailSender;

    @Test
    void queuesEscapedCustomerConfirmationAndAdminNotification() {
        when(inquiryRepository.getForAdmin(42L)).thenReturn(Optional.of(Map.of(
            "created_at", "2026-10-22 11:00:00"
        )));
        EmailOutboxService service = new EmailOutboxService(
            outboxRepository, inquiryRepository, mailSender,
            "business@example.com", "admin@example.com", "https://solar.example/admin.html"
        );
        InquiryRequest request = new InquiryRequest();
        request.setName("<b>Sam</b>");
        request.setEmail("sam@example.com");
        request.setCustomerType("Homeowner");
        request.setEnquiryType("Solar quote");
        request.setMessage("<script>hello</script>");

        service.queueInitialMessages(42L, request);

        ArgumentCaptor<String> htmlBodies = ArgumentCaptor.forClass(String.class);
        verify(outboxRepository, times(2)).enqueue(
            eq(42L), isNull(), anyString(), eq("business@example.com"), anyString(),
            anyString(), anyString(), htmlBodies.capture()
        );
        assertTrue(htmlBodies.getAllValues().get(0).contains("&lt;b&gt;Sam&lt;/b&gt;"));
        assertTrue(htmlBodies.getAllValues().get(0).contains("&lt;script&gt;hello&lt;/script&gt;"));
        assertTrue(htmlBodies.getAllValues().get(1).contains("mailto:sam@example.com"));
        assertTrue(htmlBodies.getAllValues().get(1).contains("https://solar.example/admin.html"));
    }

    @Test
    void smtpFailureIsRecordedAndRetriedWithoutThrowingAwayOutboxMessage() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(outboxRepository.findDueIds()).thenReturn(List.of(7L));
        when(outboxRepository.claim(7L)).thenReturn(true);
        when(outboxRepository.findMessage(7L)).thenReturn(Optional.of(new EmailOutboxMessage(
            7L, 42L, "ADMIN_REPLY", "business@example.com", "customer@example.com",
            "Re: enquiry", "Hello", "<p>Hello</p>", 1
        )));
        when(outboxRepository.findConfirmationMessageId(42L)).thenReturn(Optional.empty());
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("unavailable")).when(mailSender).send(mimeMessage);
        EmailOutboxService service = new EmailOutboxService(
            outboxRepository, inquiryRepository, mailSender,
            "business@example.com", "admin@example.com", "https://solar.example/admin.html"
        );

        service.deliverDueMessages();

        verify(outboxRepository).markFailed(
            eq(7L), any(Instant.class),
            eq("Mail delivery failed (MailSendException): unavailable"), eq(true)
        );
    }

    @Test
    void adminNotificationReplyToPointsToSavedCustomerAddress() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(outboxRepository.findDueIds()).thenReturn(List.of(8L));
        when(outboxRepository.claim(8L)).thenReturn(true);
        when(outboxRepository.findMessage(8L)).thenReturn(Optional.of(new EmailOutboxMessage(
            8L, 42L, "ADMIN_NOTIFICATION", "business@example.com", "admin@example.com",
            "New enquiry", "Details", "<p>Details</p>", 1
        )));
        when(inquiryRepository.getForAdmin(42L)).thenReturn(Optional.of(Map.of(
            "email", "customer@example.com"
        )));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        EmailOutboxService service = new EmailOutboxService(
            outboxRepository, inquiryRepository, mailSender,
            "business@example.com", "admin@example.com", "https://solar.example/admin.html"
        );

        service.deliverDueMessages();

        assertNotNull(mimeMessage.getHeader("Reply-To"));
        assertTrue(mimeMessage.getHeader("Reply-To")[0].contains("customer@example.com"));
        verify(outboxRepository).markSent(8L, "<tam-solar-8@example.com>", false, 42L);
    }

    @Test
    void sendsCustomerConfirmationAndAdminNotificationToTheirConfiguredRecipients() throws Exception {
        when(outboxRepository.findDueIds()).thenReturn(List.of(9L, 10L));
        when(outboxRepository.claim(9L)).thenReturn(true);
        when(outboxRepository.claim(10L)).thenReturn(true);
        when(outboxRepository.findMessage(9L)).thenReturn(Optional.of(new EmailOutboxMessage(
            9L, 42L, "CUSTOMER_CONFIRMATION", "business@example.com", "customer@example.com",
            "Enquiry received", "Thanks", "<p>Thanks</p>", 1
        )));
        when(outboxRepository.findMessage(10L)).thenReturn(Optional.of(new EmailOutboxMessage(
            10L, 42L, "ADMIN_NOTIFICATION", "business@example.com", "admin@example.com",
            "New enquiry", "Details", "<p>Details</p>", 1
        )));
        when(inquiryRepository.getForAdmin(42L)).thenReturn(Optional.of(Map.of(
            "email", "customer@example.com"
        )));
        when(mailSender.createMimeMessage()).thenAnswer(
            invocation -> new MimeMessage(Session.getInstance(new Properties()))
        );
        EmailOutboxService service = new EmailOutboxService(
            outboxRepository, inquiryRepository, mailSender,
            "business@example.com", "admin@example.com", "https://solar.example/admin.html"
        );

        service.deliverDueMessages();

        ArgumentCaptor<MimeMessage> messages = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(2)).send(messages.capture());
        Address[] customerRecipients = messages.getAllValues().get(0).getAllRecipients();
        Address[] adminRecipients = messages.getAllValues().get(1).getAllRecipients();
        assertEquals("customer@example.com", ((InternetAddress) customerRecipients[0]).getAddress());
        assertEquals("admin@example.com", ((InternetAddress) adminRecipients[0]).getAddress());
        verify(outboxRepository).markSent(9L, "<tam-solar-9@example.com>", false, 42L);
        verify(outboxRepository).markSent(10L, "<tam-solar-10@example.com>", false, 42L);
    }

    @Test
    void adminReplyRetryWithSameKeyReturnsSavedReplyWithoutQueueingAnother() {
        when(outboxRepository.findByIdempotencyKey("reply-key")).thenReturn(Optional.of(
            new EmailOutboxRepository.IdempotentReply(
                12L, 42L, "Re: [TS-000042] Solar quote follow-up", "Thank you."
            )
        ));
        EmailOutboxService service = new EmailOutboxService(
            outboxRepository, inquiryRepository, mailSender,
            "business@example.com", "admin@example.com", "https://solar.example/admin.html"
        );

        assertEquals(12L, service.queueAdminReply(
            42L, "Solar quote follow-up", "Thank you.", "reply-key"
        ));
        assertThrows(IllegalArgumentException.class, () -> service.queueAdminReply(
            42L, "A different subject", "Thank you.", "reply-key"
        ));
        verify(outboxRepository, times(2)).findByIdempotencyKey("reply-key");
    }
}
