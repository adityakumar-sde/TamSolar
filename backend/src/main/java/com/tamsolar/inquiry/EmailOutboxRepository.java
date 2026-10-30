package com.tamsolar.inquiry;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class EmailOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmailOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long enqueue(
            long inquiryId,
            String idempotencyKey,
            String kind,
            String sender,
            String recipient,
            String subject,
            String textBody,
            String htmlBody) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO email_outbox (inquiry_id, idempotency_key, kind, sender, recipient, subject, text_body, html_body) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, inquiryId);
            ps.setString(2, idempotencyKey);
            ps.setString(3, kind);
            ps.setString(4, sender);
            ps.setString(5, recipient);
            ps.setString(6, subject);
            ps.setString(7, textBody);
            ps.setString(8, htmlBody);
            return ps;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public Optional<IdempotentReply> findByIdempotencyKey(String key) {
        List<IdempotentReply> matches = jdbcTemplate.query(
                "SELECT id, inquiry_id, subject, text_body "
                        + "FROM email_outbox "
                        + "WHERE idempotency_key = ?",
                (resultSet, rowNum) -> new IdempotentReply(
                        resultSet.getLong("id"),
                        resultSet.getLong("inquiry_id"),
                        resultSet.getString("subject"),
                        resultSet.getString("text_body")),
                key);

        return matches.stream().findFirst();
    }

    public List<Long> findDueIds() {
        String sql = "SELECT id "
                + "FROM email_outbox "
                + "WHERE (status = 'PENDING' "
                + "AND next_attempt_at <= CURRENT_TIMESTAMP) "
                + "OR (status = 'SENDING' "
                + "AND claimed_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE)) "
                + "ORDER BY id "
                + "LIMIT 20";

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> resultSet.getLong("id"));
    }

    public boolean claim(long id) {
        String sql = "UPDATE email_outbox "
                + "SET status = 'SENDING', "
                + "attempts = attempts + 1, "
                + "claimed_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? "
                + "AND ((status = 'PENDING' "
                + "AND next_attempt_at <= CURRENT_TIMESTAMP) "
                + "OR (status = 'SENDING' "
                + "AND claimed_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE)))";

        return jdbcTemplate.update(sql, id) == 1;
    }

    public Optional<EmailOutboxMessage> findMessage(long id) {
        String sql = "SELECT id, inquiry_id, kind, sender, recipient, subject, "
                + "text_body, html_body, attempts "
                + "FROM email_outbox "
                + "WHERE id = ?";

        List<EmailOutboxMessage> messages = jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new EmailOutboxMessage(
                        resultSet.getLong("id"),
                        resultSet.getLong("inquiry_id"),
                        resultSet.getString("kind"),
                        resultSet.getString("sender"),
                        resultSet.getString("recipient"),
                        resultSet.getString("subject"),
                        resultSet.getString("text_body"),
                        resultSet.getString("html_body"),
                        resultSet.getInt("attempts")),
                id);

        return messages.stream().findFirst();
    }

    public Optional<String> findConfirmationMessageId(long inquiryId) {
        String sql = "SELECT message_id "
                + "FROM email_outbox "
                + "WHERE inquiry_id = ? "
                + "AND kind = 'CUSTOMER_CONFIRMATION' "
                + "AND status = 'SENT' "
                + "AND message_id IS NOT NULL "
                + "ORDER BY id "
                + "LIMIT 1";

        List<String> messageIds = jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> resultSet.getString("message_id"),
                inquiryId);

        return messageIds.stream().findFirst();
    }

    public void markSent(
            long id,
            String messageId,
            boolean adminReply,
            long inquiryId) {
        String sql = "UPDATE email_outbox "
                + "SET status = 'SENT', "
                + "message_id = ?, "
                + "sent_at = CURRENT_TIMESTAMP, "
                + "claimed_at = NULL, "
                + "last_error = NULL "
                + "WHERE id = ?";

        jdbcTemplate.update(sql, messageId, id);

        if (adminReply) {
            jdbcTemplate.update(
                    "UPDATE inquiry_workflow "
                            + "SET status = 'REPLIED' "
                            + "WHERE inquiry_id = ?",
                    inquiryId);
        }
    }

    public void markFailed(
            long id,
            Instant nextAttempt,
            String error,
            boolean retry) {
        String sql = "UPDATE email_outbox "
                + "SET status = ?, "
                + "next_attempt_at = ?, "
                + "claimed_at = NULL, "
                + "last_error = ? "
                + "WHERE id = ?";

        jdbcTemplate.update(
                sql,
                retry ? "PENDING" : "FAILED",
                Timestamp.from(nextAttempt),
                error,
                id);
    }

    public List<Map<String, Object>> getDeliveryStatuses(long inquiryId) {
        String sql = "SELECT id, kind, status, attempts, last_error, "
                + "created_at, sent_at, subject, sender, recipient, text_body "
                + "FROM email_outbox "
                + "WHERE inquiry_id = ? "
                + "ORDER BY created_at, id";

        return jdbcTemplate.queryForList(sql, inquiryId);
    }

    public record IdempotentReply(
            long id,
            long inquiryId,
            String subject,
            String message) {
    }
}
