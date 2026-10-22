
package com.tamsolar.inquiry;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailOutboxService {

    private static final Logger logger = LoggerFactory.getLogger(EmailOutboxService.class);

    private static final int MAX_ATTEMPTS = 8;
    private static final int MAX_FAILURE_DETAIL_LENGTH = 500;

    private final EmailOutboxRepository outboxRepository;
    private final InquiryRepository inquiryRepository;
    private final JavaMailSender mailSender;
    private final String from;
    private final String adminEmail;
    private final String adminUrl;

    public EmailOutboxService(
            EmailOutboxRepository outboxRepository,
            InquiryRepository inquiryRepository,
            JavaMailSender mailSender,
            @Value("${tamsolar.mail.from}") String from,
            @Value("${tamsolar.admin.email}") String adminEmail,
            @Value("${tamsolar.admin.url}") String adminUrl) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.inquiryRepository = Objects.requireNonNull(inquiryRepository);
        this.mailSender = Objects.requireNonNull(mailSender);
        this.from = requireText(from, "tamsolar.mail.from");
        this.adminEmail = requireText(adminEmail, "tamsolar.admin.email");
        this.adminUrl = requireText(adminUrl, "tamsolar.admin.url");
    }

    /**
     * Queue the customer confirmation and administrator notification.
     * Actual delivery is handled by the scheduled outbox processor.
     */
    public void queueInitialMessages(long id, InquiryRequest request) {
        Objects.requireNonNull(request, "Inquiry request cannot be null");

        String name = requireText(request.getName(), "Customer name");
        String customerEmail = requireText(request.getEmail(), "Customer email");
        String customerType = safeValue(request.getCustomerType(), "Not specified");
        String enquiryType = safeValue(request.getEnquiryType(), "Not specified");
        String message = safeValue(request.getMessage(), "No message provided");
        String reference = referenceFor(id);

        String customerText = """
                Dear %s,

                Thank you for contacting Tam Solar.

                We have successfully received your enquiry. Our team will review your requirements and contact you soon.

                Enquiry Reference: %s

                Your Enquiry Details:
                - Name: %s
                - Email: %s
                - Customer type: %s
                - Enquiry topic: %s
                - Message: %s

                Please keep your enquiry reference for future communication.

                Regards,
                Tam Solar Team
                """.formatted(
                name, reference, name, customerEmail,
                customerType, enquiryType, message);

        String customerHtml = """
                <!DOCTYPE html>
                <html>
                <body style="font-family:Arial,sans-serif;color:#20382e;line-height:1.6">
                    <p>Dear %s,</p>
                    <p>Thank you for contacting Tam Solar. We have successfully received your enquiry.
                    Our team will review your requirements and contact you soon.</p>
                    <p><strong>Enquiry Reference: %s</strong></p>
                    <h3>Your Enquiry Details</h3>
                    <ul>
                        <li><strong>Name:</strong> %s</li>
                        <li><strong>Email:</strong> %s</li>
                        <li><strong>Customer type:</strong> %s</li>
                        <li><strong>Enquiry topic:</strong> %s</li>
                        <li><strong>Message:</strong><br>%s</li>
                    </ul>
                    <p>Please keep your enquiry reference for future communication.</p>
                    <p>Regards,<br><strong>Tam Solar Team</strong></p>
                </body>
                </html>
                """.formatted(
                escape(name),
                escape(reference),
                escape(name),
                escape(customerEmail),
                escape(customerType),
                escape(enquiryType),
                escapeWithLineBreaks(message));

        outboxRepository.enqueue(
                id,
                null,
                "CUSTOMER_CONFIRMATION",
                from,
                customerEmail,
                "Your Solar Enquiry Has Been Received - Tam Solar",
                customerText,
                customerHtml);

        String submittedAt = inquiryRepository.getForAdmin(id)
                .map(inquiry -> safeValue(inquiry.get("created_at"), "Not available"))
                .orElse("Not available");

        String adminText = """
                New website enquiry: %s

                Submitted: %s
                Name: %s
                Email: %s
                Customer type: %s
                Enquiry topic: %s
                Message: %s

                Admin panel: %s
                """.formatted(
                reference, submittedAt, name, customerEmail,
                customerType, enquiryType, message, adminUrl);

        String adminHtml = """
                <!DOCTYPE html>
                <html>
                <body style="font-family:Arial,sans-serif;color:#20382e;line-height:1.6">
                    <h2>New website enquiry: %s</h2>
                    <p><strong>Submitted:</strong> %s</p>
                    <p>
                        <strong>Customer:</strong> %s<br>
                        <strong>Email:</strong>
                        <a href="mailto:%s">%s</a><br>
                        <strong>Customer type:</strong> %s<br>
                        <strong>Enquiry topic:</strong> %s
                    </p>
                    <h3>Message</h3>
                    <p>%s</p>
                    <p><a href="%s">Open admin panel</a></p>
                </body>
                </html>
                """.formatted(
                escape(reference),
                escape(submittedAt),
                escape(name),
                escape(customerEmail),
                escape(customerEmail),
                escape(customerType),
                escape(enquiryType),
                escapeWithLineBreaks(message),
                escape(adminUrl));

        outboxRepository.enqueue(
                id,
                null,
                "ADMIN_NOTIFICATION",
                from,
                adminEmail,
                "New Website Enquiry - " + reference,
                adminText,
                adminHtml);
    }

    /**
     * Queue an administrator reply to a customer's enquiry.
     */
    public long queueAdminReply(
            long inquiryId,
            String subject,
            String body,
            String idempotencyKey) {
        String cleanSubject = requireText(subject, "Reply subject");
        String cleanBody = requireText(body, "Reply body");
        String reference = referenceFor(inquiryId);
        String safeSubject = "Re: [" + reference + "] " + cleanSubject;

        if (StringUtils.hasText(idempotencyKey)) {
            var existing = outboxRepository.findByIdempotencyKey(idempotencyKey);

            if (existing.isPresent()) {
                var reply = existing.get();

                verifySameReply(
                        reply.inquiryId(),
                        reply.subject(),
                        reply.message(),
                        inquiryId,
                        safeSubject,
                        cleanBody);

                return reply.id();
            }
        }

        Map<String, Object> inquiry = inquiryRepository.getForAdmin(inquiryId)
                .orElseThrow(() -> new InquiryNotFoundException(inquiryId));

        String recipient = requireText(
                safeValue(inquiry.get("email"), ""),
                "Customer email in inquiry");

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="font-family:Arial,sans-serif;color:#20382e;line-height:1.6">
                    <div>%s</div>
                    <hr style="border:none;border-top:1px solid #ddd;margin-top:24px">
                    <p style="color:#68736c;font-size:12px">
                        Enquiry reference: %s
                    </p>
                    <p>Regards,<br><strong>Tam Solar Team</strong></p>
                </body>
                </html>
                """.formatted(escapeWithLineBreaks(cleanBody), escape(reference));

        try {
            return outboxRepository.enqueue(
                    inquiryId,
                    idempotencyKey,
                    "ADMIN_REPLY",
                    from,
                    recipient,
                    safeSubject,
                    cleanBody,
                    html);
        } catch (DuplicateKeyException duplicate) {
            if (!StringUtils.hasText(idempotencyKey)) {
                throw duplicate;
            }

            var reply = outboxRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> duplicate);

            verifySameReply(
                    reply.inquiryId(),
                    reply.subject(),
                    reply.message(),
                    inquiryId,
                    safeSubject,
                    cleanBody);

            return reply.id();
        }
    }

    /**
     * Poll the outbox for due messages.
     * Keep this method aligned with the existing repository claim/retry logic.
     */
    @Scheduled(fixedDelayString = "${tamsolar.email.retry-delay-ms:15000}")
    public void deliverDueMessages() {
        List<Long> dueIds = outboxRepository.findDueIds();

        if (dueIds == null || dueIds.isEmpty()) {
            return;
        }

        for (Long id : dueIds) {
            if (id == null) {
                continue;
            }

            try {
                deliver(id);
            } catch (RuntimeException exception) {
                // Keep one unexpected database/programming error from
                // preventing other due messages from being attempted.
                logger.error(
                        "Unexpected outbox processing error for message {}",
                        id,
                        exception);
            }
        }
    }

    private void deliver(long id) {
        if (!outboxRepository.claim(id)) {
            return;
        }

        EmailOutboxMessage message = outboxRepository.findMessage(id)
                .orElseThrow(() -> new IllegalStateException("Claimed outbox message not found: " + id));

        try {
            String messageId = send(message);

            outboxRepository.markSent(
                    id,
                    messageId,
                    "ADMIN_REPLY".equals(message.kind()),
                    message.inquiryId());

            logger.info(
                    "Email outbox message {} sent successfully to {}",
                    id,
                    message.recipient());
        } catch (MailException | MessagingException exception) {
            boolean retry = message.attempts() < MAX_ATTEMPTS;

            int attempt = Math.max(1, message.attempts());
            long delaySeconds = Math.min(
                    3600L,
                    30L << Math.min(attempt - 1, 7));

            String failure = failureMessage(exception);

            outboxRepository.markFailed(
                    id,
                    Instant.now().plusSeconds(delaySeconds),
                    failure,
                    retry);

            logger.warn(
                    "Email outbox delivery failed for message {} (attempt {}, retry={}): {}",
                    id,
                    message.attempts(),
                    retry,
                    failure);
        }
    }

    private String send(EmailOutboxMessage message)
            throws MessagingException {

        MimeMessage mimeMessage = mailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                true,
                StandardCharsets.UTF_8.name());

        helper.setFrom(message.sender());
        helper.setTo(message.recipient());
        helper.setSubject(message.subject());
        helper.setText(message.textBody(), message.htmlBody());

        if ("ADMIN_NOTIFICATION".equals(message.kind())) {
            inquiryRepository.getForAdmin(message.inquiryId())
                    .map(inquiry -> safeValue(inquiry.get("email"), ""))
                    .filter(StringUtils::hasText)
                    .ifPresent(email -> {
                        try {
                            helper.setReplyTo(email);
                        } catch (MessagingException exception) {
                            throw new IllegalArgumentException(
                                    "Could not configure administrator notification Reply-To",
                                    exception);
                        }
                    });
        } else {
            helper.setReplyTo(from);
        }

        String domain = from.substring(from.lastIndexOf('@') + 1);
        String messageId = "<tam-solar-" + message.id() + "@" + domain + ">";

        mimeMessage.setHeader("Message-ID", messageId);

        if ("ADMIN_REPLY".equals(message.kind())) {
            outboxRepository.findConfirmationMessageId(message.inquiryId())
                    .filter(StringUtils::hasText)
                    .ifPresent(originalMessageId -> {
                        try {
                            mimeMessage.setHeader("In-Reply-To", originalMessageId);
                            mimeMessage.setHeader("References", originalMessageId);
                        } catch (MessagingException exception) {
                            throw new IllegalArgumentException(
                                    "Could not configure reply email headers",
                                    exception);
                        }
                    });
        }

        mailSender.send(mimeMessage);
        return messageId;
    }

    private static void verifySameReply(
            long existingInquiryId,
            String existingSubject,
            String existingBody,
            long inquiryId,
            String subject,
            String body) {
        if (existingInquiryId != inquiryId
                || !Objects.equals(existingSubject, subject)
                || !Objects.equals(existingBody, body)) {
            throw new IllegalArgumentException(
                    "This idempotency key was already used for another reply.");
        }
    }

    private static String referenceFor(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Inquiry ID must be positive.");
        }

        return "TS-%06d".formatted(id);
    }

    private static String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        return value.trim();
    }

    private static String safeValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String result = String.valueOf(value).trim();
        return result.isEmpty() ? fallback : result;
    }

    private static String escapeWithLineBreaks(String value) {
        return escape(value).replace("\r\n", "\n").replace("\r", "\n")
                .replace("\n", "<br>");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String failureMessage(Exception exception) {
        String prefix = "Mail delivery failed ("
                + exception.getClass().getSimpleName() + ")";

        String detail = exception.getMessage();

        if (detail == null || detail.isBlank()) {
            return prefix;
        }

        detail = detail.replaceAll("\\s+", " ").trim();

        int maxDetailLength = Math.max(
                0,
                MAX_FAILURE_DETAIL_LENGTH - prefix.length() - 2);

        if (detail.length() > maxDetailLength) {
            detail = detail.substring(0, maxDetailLength);
        }

        return prefix + ": " + detail;
    }
}
