
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

                Thank you for choosing TAM Solar Solutions for your solar energy requirements!

                Your inquiry has been successfully received. Our team is reviewing your
                requirements and will contact you shortly.

                YOUR INQUIRY SUMMARY
                -----------------------------------------------
                Reference Number : %s
                Service          : %s
                Customer Type    : %s
                Status           : Inquiry Received

                WHAT HAPPENS NEXT?
                -----------------------------------------------
                01 - Requirement Review
                     Our team reviews your solar installation requirements.

                02 - Consultation
                     We contact you to understand your property and energy needs.

                03 - Customised Quotation
                     We prepare a proposal tailored to your requirements.

                04 - Installation Planning
                     Once you approve the proposal, we discuss the next steps.

                Thank you for giving us the opportunity to support your transition
                to clean energy.

                Warm regards,
                TAM Solar Solutions - Customer Support Team
                Email: solarsolutionstam@gmail.com
                Phone: +91 83830 63590
                """.formatted(
                name, reference, enquiryType, customerType);

        String customerHtml = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>TAM Solar Solutions - Inquiry Confirmation</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f4f6f2;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#16251f">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f6f2;padding:36px 16px">
                    <tr>
                      <td align="center">
                        <table width="600" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 6px 28px rgba(0,0,0,0.08);max-width:600px;border:1px solid #e4e8df">

                          <!-- PROPER BRAND HEADER -->
                          <tr>
                            <td style="background:#16251f;padding:36px 40px;text-align:center;border-bottom:3px solid #d9ef7a">
                              <h1 style="margin:0;font-size:26px;font-weight:800;color:#d9ef7a;letter-spacing:-0.5px;text-transform:uppercase">
                                TAM SOLAR SOLUTIONS
                              </h1>
                              <p style="margin:6px 0 0;font-size:12px;font-weight:600;color:rgba(255,255,255,0.7);letter-spacing:1.8px;text-transform:uppercase">
                                Power a Brighter Tomorrow
                              </p>
                            </td>
                          </tr>

                          <!-- GREETING -->
                          <tr>
                            <td style="padding:36px 40px 20px">
                              <p style="margin:0 0 14px;font-size:20px;font-weight:700;color:#16251f">
                                Dear %s,
                              </p>
                              <p style="margin:0;font-size:15px;color:#374151;line-height:1.75">
                                Thank you for choosing <strong>TAM Solar Solutions</strong> for your solar energy requirements!
                              </p>
                              <p style="margin:12px 0 0;font-size:15px;color:#374151;line-height:1.75">
                                Your inquiry has been successfully received. Our team is reviewing your requirements and will contact you shortly.
                              </p>
                            </td>
                          </tr>

                          <!-- YOUR INQUIRY SUMMARY -->
                          <tr>
                            <td style="padding:10px 40px 24px">
                              <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f8faf6;border:1px solid #e4e8df;border-radius:10px;overflow:hidden">
                                <tr>
                                  <td colspan="2" style="background:#edf3e8;padding:14px 20px;border-bottom:1px solid #e4e8df">
                                    <span style="font-size:11px;font-weight:800;letter-spacing:1.8px;color:#274438;text-transform:uppercase">
                                      YOUR INQUIRY SUMMARY
                                    </span>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:12px 20px;font-size:13px;color:#68736c;border-bottom:1px solid #eef1ea;width:42%%">Reference Number</td>
                                  <td style="padding:12px 20px;font-size:14px;font-weight:800;color:#16251f;border-bottom:1px solid #eef1ea">%s</td>
                                </tr>
                                <tr>
                                  <td style="padding:12px 20px;font-size:13px;color:#68736c;border-bottom:1px solid #eef1ea">Service</td>
                                  <td style="padding:12px 20px;font-size:13px;font-weight:700;color:#16251f;border-bottom:1px solid #eef1ea">%s</td>
                                </tr>
                                <tr>
                                  <td style="padding:12px 20px;font-size:13px;color:#68736c;border-bottom:1px solid #eef1ea">Customer Type</td>
                                  <td style="padding:12px 20px;font-size:13px;font-weight:600;color:#16251f;border-bottom:1px solid #eef1ea">%s</td>
                                </tr>
                                <tr>
                                  <td style="padding:12px 20px;font-size:13px;color:#68736c">Status</td>
                                  <td style="padding:12px 20px">
                                    <span style="display:inline-block;background:#d9ef7a;color:#16251f;font-size:11px;font-weight:800;padding:4px 12px;border-radius:20px;text-transform:uppercase;letter-spacing:0.5px">
                                      Inquiry Received
                                    </span>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <!-- WHAT HAPPENS NEXT -->
                          <tr>
                            <td style="padding:10px 40px 24px">
                              <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border:1px solid #e4e8df;border-radius:10px;overflow:hidden">
                                <tr>
                                  <td style="background:#16251f;padding:12px 20px">
                                    <span style="font-size:11px;font-weight:800;letter-spacing:1.8px;color:#d9ef7a;text-transform:uppercase">
                                      WHAT HAPPENS NEXT?
                                    </span>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:16px 20px">
                                    <table width="100%%" cellpadding="0" cellspacing="0">
                                      <tr>
                                        <td style="padding:10px 0;border-bottom:1px solid #f0f2ed;vertical-align:top">
                                          <table cellpadding="0" cellspacing="0"><tr>
                                            <td style="width:34px;height:34px;background:#16251f;border-radius:50%%;text-align:center;vertical-align:middle;font-size:11px;font-weight:800;color:#d9ef7a;min-width:34px">01</td>
                                            <td style="padding-left:14px">
                                              <p style="margin:0;font-size:13px;font-weight:700;color:#16251f">Requirement Review</p>
                                              <p style="margin:2px 0 0;font-size:12px;color:#68736c">Our team reviews your solar installation requirements.</p>
                                            </td>
                                          </tr></table>
                                        </td>
                                      </tr>
                                      <tr>
                                        <td style="padding:10px 0;border-bottom:1px solid #f0f2ed;vertical-align:top">
                                          <table cellpadding="0" cellspacing="0"><tr>
                                            <td style="width:34px;height:34px;background:#16251f;border-radius:50%%;text-align:center;vertical-align:middle;font-size:11px;font-weight:800;color:#d9ef7a;min-width:34px">02</td>
                                            <td style="padding-left:14px">
                                              <p style="margin:0;font-size:13px;font-weight:700;color:#16251f">Consultation</p>
                                              <p style="margin:2px 0 0;font-size:12px;color:#68736c">We contact you to understand your property and energy needs.</p>
                                            </td>
                                          </tr></table>
                                        </td>
                                      </tr>
                                      <tr>
                                        <td style="padding:10px 0;border-bottom:1px solid #f0f2ed;vertical-align:top">
                                          <table cellpadding="0" cellspacing="0"><tr>
                                            <td style="width:34px;height:34px;background:#16251f;border-radius:50%%;text-align:center;vertical-align:middle;font-size:11px;font-weight:800;color:#d9ef7a;min-width:34px">03</td>
                                            <td style="padding-left:14px">
                                              <p style="margin:0;font-size:13px;font-weight:700;color:#16251f">Customized Quotation</p>
                                              <p style="margin:2px 0 0;font-size:12px;color:#68736c">We prepare a proposal tailored to your requirements.</p>
                                            </td>
                                          </tr></table>
                                        </td>
                                      </tr>
                                      <tr>
                                        <td style="padding:10px 0;vertical-align:top">
                                          <table cellpadding="0" cellspacing="0"><tr>
                                            <td style="width:34px;height:34px;background:#16251f;border-radius:50%%;text-align:center;vertical-align:middle;font-size:11px;font-weight:800;color:#d9ef7a;min-width:34px">04</td>
                                            <td style="padding-left:14px">
                                              <p style="margin:0;font-size:13px;font-weight:700;color:#16251f">Installation Planning</p>
                                              <p style="margin:2px 0 0;font-size:12px;color:#68736c">Once you approve the proposal, we discuss the next steps.</p>
                                            </td>
                                          </tr></table>
                                        </td>
                                      </tr>
                                    </table>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <!-- CLOSING -->
                          <tr>
                            <td style="padding:0 40px 28px">
                              <p style="margin:0;font-size:14px;color:#4a5568;line-height:1.75">
                                Thank you for giving us the opportunity to support your transition to clean energy.
                              </p>
                            </td>
                          </tr>

                          <!-- FOOTER -->
                          <tr>
                            <td style="background:#16251f;padding:28px 40px;text-align:center;border-top:1px solid rgba(255,255,255,0.1)">
                              <p style="margin:0 0 4px;font-size:15px;font-weight:800;color:#d9ef7a;letter-spacing:0.5px">TAM Solar Solutions</p>
                              <p style="margin:0 0 12px;font-size:12px;color:rgba(255,255,255,0.6)">Customer Support Team</p>
                              <p style="margin:0;font-size:12px;color:rgba(255,255,255,0.5)">
                                Email: <a href="mailto:solarsolutionstam@gmail.com" style="color:#d9ef7a;text-decoration:none;font-weight:600">solarsolutionstam@gmail.com</a>
                                &nbsp;&bull;&nbsp;
                                Phone: <a href="tel:+918383063590" style="color:#ffffff;text-decoration:none">+91 83830 63590</a>
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escape(name),
                escape(reference),
                escape(enquiryType),
                escape(customerType));

        outboxRepository.enqueue(
                id,
                null,
                "CUSTOMER_CONFIRMATION",
                from,
                customerEmail,
                "Your Solar Inquiry Has Been Received - TAM Solar Solutions (" + reference + ")",
                customerText,
                customerHtml);
        String submittedAt = inquiryRepository.getForAdmin(id)
                .map(inquiry -> safeValue(inquiry.get("created_at"), "Not available"))
                .orElse("Not available");

        String adminText = """
                Dear Admin Team,

                A new customer inquiry has been received through the TAM Solar Solutions website.

                CUSTOMER OVERVIEW
                -----------------------------------------------
                Customer Name  : %s
                Email Address  : %s
                Customer Type  : %s
                Service        : %s
                Submitted At   : %s

                CUSTOMER REQUIREMENTS
                -----------------------------------------------
                "%s"

                NEXT ACTION
                -----------------------------------------------
                1. Review the customer requirements.
                2. Contact the customer.
                3. Prepare a quotation.
                4. Update the inquiry status in the admin panel.

                Inquiry Reference: %s
                Admin Panel: %s

                This is an automated notification from the TAM Solar Solutions website.

                Regards,
                TAM Solar Solutions
                Admin Notification System
                """.formatted(
                name, customerEmail, customerType, enquiryType,
                submittedAt, message, reference, adminUrl);

        String adminHtml = """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background:#f0f2ef;font-family:Arial,sans-serif">
                  <table width="100%%" cellpadding="0" cellspacing="0"
                         style="background:#f0f2ef;padding:32px 0">
                    <tr><td align="center">
                      <table width="620" cellpadding="0" cellspacing="0"
                             style="background:#fff;border-radius:10px;overflow:hidden;
                                    box-shadow:0 4px 20px rgba(0,0,0,0.08);max-width:620px">
                        <tr>
                          <td style="background:#16251f;padding:26px 40px">
                            <table width="100%%"><tr>
                              <td>
                                <p style="margin:0;font-size:20px;font-weight:700;color:#d9ef7a">
                                  TAM Solar Solutions</p>
                                <p style="margin:4px 0 0;font-size:10px;
                                          color:rgba(255,255,255,0.5);letter-spacing:2px;
                                          text-transform:uppercase">Admin Notification</p>
                              </td>
                              <td align="right">
                                <span style="background:rgba(217,239,122,0.15);color:#d9ef7a;
                                             font-size:10px;font-weight:700;padding:5px 14px;
                                             border-radius:20px;
                                             border:1px solid rgba(217,239,122,0.3)">
                                  NEW INQUIRY</span>
                              </td>
                            </tr></table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 40px 0">
                            <p style="margin:0 0 6px;font-size:17px;font-weight:700;color:#16251f">
                              Dear Admin Team,</p>
                            <p style="margin:0;font-size:14px;color:#4a5568;line-height:1.7">
                              A new customer inquiry has been received through the
                              TAM Solar Solutions website.
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:22px 40px 0">
                            <table width="100%%" cellpadding="0" cellspacing="0"
                                   style="background:#f8faf5;border:1px solid #e4e8df;
                                          border-radius:8px;overflow:hidden">
                              <tr><td style="padding:16px 22px 6px">
                                <p style="margin:0 0 12px;font-size:10px;font-weight:700;
                                          letter-spacing:2px;color:#78934b;text-transform:uppercase">
                                  CUSTOMER OVERVIEW</p>
                              </td></tr>
                              <tr><td style="padding:0 22px 7px">
                                <table width="100%%"><tr>
                                  <td style="font-size:12px;color:#68736c;width:38%%;padding:4px 0">
                                    Customer Name</td>
                                  <td style="font-size:13px;font-weight:600;color:#16251f;padding:4px 0">
                                    %s</td>
                                </tr></table>
                              </td></tr>
                              <tr><td style="padding:0 22px 7px">
                                <table width="100%%"><tr>
                                  <td style="font-size:12px;color:#68736c;width:38%%;padding:4px 0">
                                    Email Address</td>
                                  <td style="font-size:13px;color:#16251f;padding:4px 0">
                                    <a href="mailto:%s"
                                       style="color:#274438;font-weight:600;text-decoration:none">
                                      %s</a></td>
                                </tr></table>
                              </td></tr>
                              <tr><td style="padding:0 22px 7px">
                                <table width="100%%"><tr>
                                  <td style="font-size:12px;color:#68736c;width:38%%;padding:4px 0">
                                    Customer Type</td>
                                  <td style="font-size:13px;font-weight:600;color:#16251f;padding:4px 0">
                                    %s</td>
                                </tr></table>
                              </td></tr>
                              <tr><td style="padding:0 22px 7px">
                                <table width="100%%"><tr>
                                  <td style="font-size:12px;color:#68736c;width:38%%;padding:4px 0">
                                    Service</td>
                                  <td style="font-size:13px;font-weight:600;color:#16251f;padding:4px 0">
                                    %s</td>
                                </tr></table>
                              </td></tr>
                              <tr><td style="padding:0 22px 16px">
                                <table width="100%%"><tr>
                                  <td style="font-size:12px;color:#68736c;width:38%%;padding:4px 0">
                                    Submitted At</td>
                                  <td style="font-size:13px;color:#16251f;padding:4px 0">%s</td>
                                </tr></table>
                              </td></tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 40px 0">
                            <p style="margin:0 0 10px;font-size:10px;font-weight:700;
                                      letter-spacing:2px;color:#78934b;text-transform:uppercase">
                              CUSTOMER REQUIREMENTS</p>
                            <div style="background:#f8faf5;border-left:3px solid #d9ef7a;
                                        border-radius:0 8px 8px 0;padding:14px 18px">
                              <p style="margin:0;font-size:14px;color:#16251f;line-height:1.75;
                                        font-style:italic">&ldquo;%s&rdquo;</p>
                            </div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 40px 0">
                            <p style="margin:0 0 10px;font-size:10px;font-weight:700;
                                      letter-spacing:2px;color:#78934b;text-transform:uppercase">
                              NEXT ACTION</p>
                            <table width="100%%" cellpadding="0" cellspacing="0">
                              <tr><td style="padding:5px 0;font-size:13px;color:#4a5568">
                                <span style="font-weight:700;color:#16251f">1.</span>
                                &nbsp;Review the customer requirements.
                              </td></tr>
                              <tr><td style="padding:5px 0;font-size:13px;color:#4a5568">
                                <span style="font-weight:700;color:#16251f">2.</span>
                                &nbsp;Contact the customer via
                                <a href="mailto:%s"
                                   style="color:#274438;font-weight:600;text-decoration:none">%s</a>.
                              </td></tr>
                              <tr><td style="padding:5px 0;font-size:13px;color:#4a5568">
                                <span style="font-weight:700;color:#16251f">3.</span>
                                &nbsp;Prepare a customised quotation.
                              </td></tr>
                              <tr><td style="padding:5px 0;font-size:13px;color:#4a5568">
                                <span style="font-weight:700;color:#16251f">4.</span>
                                &nbsp;Update the inquiry status in the admin panel.
                              </td></tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 40px 28px">
                            <table width="100%%" cellpadding="0" cellspacing="0"
                                   style="background:#16251f;border-radius:8px">
                              <tr><td style="padding:16px 22px">
                                <table width="100%%"><tr>
                                  <td>
                                    <p style="margin:0;font-size:10px;
                                              color:rgba(255,255,255,0.4);letter-spacing:1.5px">
                                      INQUIRY REFERENCE</p>
                                    <p style="margin:4px 0 0;font-size:22px;font-weight:800;
                                              color:#d9ef7a;letter-spacing:-0.5px">%s</p>
                                  </td>
                                  <td align="right" style="vertical-align:middle">
                                    <a href="%s"
                                       style="display:inline-block;background:#d9ef7a;color:#16251f;
                                              font-size:12px;font-weight:700;padding:10px 18px;
                                              border-radius:6px;text-decoration:none">
                                      Open Admin Panel &rarr;</a>
                                  </td>
                                </tr></table>
                              </td></tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="background:#f8faf5;border-top:1px solid #e4e8df;
                                     padding:14px 40px;text-align:center">
                            <p style="margin:0;font-size:11px;color:#68736c">
                              This is an automated notification from the TAM Solar Solutions website.
                            </p>
                            <p style="margin:3px 0 0;font-size:11px;color:#68736c">
                              TAM Solar Solutions &mdash; Admin Notification System
                            </p>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escape(name),
                escape(customerEmail), escape(customerEmail),
                escape(customerType),
                escape(enquiryType),
                escape(submittedAt),
                escapeWithLineBreaks(message),
                escape(customerEmail), escape(customerEmail),
                escape(reference),
                escape(adminUrl));

        outboxRepository.enqueue(
                id,
                null,
                "ADMIN_NOTIFICATION",
                from,
                adminEmail,
                "New Inquiry Received - " + reference + " - TAM Solar Solutions",
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

        MimeMessage mimeMessage;
        if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl impl) {
            mimeMessage = new MimeMessage(impl.getSession()) {
                @Override
                protected void updateMessageID() throws MessagingException {
                    // Do not inject client LAN IP into Message-ID!
                    // Let Google SMTP assign its authoritative, DKIM-signed Message-ID (<...@mx.google.com>).
                }
            };
        } else {
            mimeMessage = mailSender.createMimeMessage();
        }

        MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                true,
                StandardCharsets.UTF_8.name());

        try {
            helper.setFrom(message.sender(), "TAM Solar Solutions");
        } catch (java.io.UnsupportedEncodingException e) {
            helper.setFrom(message.sender());
        }
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
            try {
                helper.setReplyTo(from, "TAM Solar Solutions");
            } catch (java.io.UnsupportedEncodingException e) {
                helper.setReplyTo(from);
            }
        }

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

        String messageId = mimeMessage.getMessageID();
        if (messageId == null || messageId.isBlank()) {
            messageId = "<tam-solar-" + message.id() + "@mx.google.com>";
        }
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
