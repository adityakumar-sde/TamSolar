
package com.tamsolar.inquiry;

public record EmailOutboxMessage(
        long id,
        long inquiryId,
        String kind,
        String sender,
        String recipient,
        String subject,
        String textBody,
        String htmlBody,
        int attempts) {
}
