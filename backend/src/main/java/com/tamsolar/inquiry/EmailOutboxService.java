
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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
        String phone = safeValue(request.getPhone(), "Not provided");
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
                Requirements     : %s
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
                name, reference, phone, enquiryType, customerType, message);

        String customerHtml = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>TAM Solar Solutions - Inquiry Confirmation</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f0f4f8;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;-webkit-font-smoothing:antialiased;color:#1e293b">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f0f4f8;padding:30px 10px">
                    <tr>
                      <td align="center">
                        <!-- EXACT 682px CARD CONTAINER (Matching uploaded image width & proportions) -->
                        <table width="682" cellpadding="0" cellspacing="0" style="width:682px;max-width:682px;background-color:#ffffff;border-radius:14px;overflow:hidden;box-shadow:0 10px 36px rgba(0,75,153,0.14);border:1px solid #dbeafe">

                          <!-- 1. HEADER BANNER (Exact 682x134 banner from image with official logo and text) -->
                          <tr>
                            <td style="padding:0;line-height:0;background:#0052a5">
                              <img src="cid:emailHeader" alt="TAM SOLAR SOLUTIONS - POWER A BRIGHTER TOMORROW" style="display:block;width:100%%;max-width:682px;height:auto;border:0;color:#ffffff;font-size:18px;font-weight:800;letter-spacing:1px;font-family:Arial,sans-serif;text-align:center" />
                            </td>
                          </tr>

                          <!-- 2. GREETING & INTRO -->
                          <tr>
                            <td style="padding:42px 60px 22px;background:#ffffff">
                              <h2 style="margin:0 0 16px;font-size:22px;font-weight:800;color:#0f172a;letter-spacing:-0.3px;line-height:1.3">
                                Dear %s,
                              </h2>
                              <p style="margin:0;font-size:15px;color:#334155;line-height:1.75">
                                Thank you for choosing <strong style="color:#0052a5">TAM Solar Solutions</strong> for your solar energy requirements!
                              </p>
                              <p style="margin:14px 0 0;font-size:15px;color:#334155;line-height:1.75">
                                Your inquiry has been successfully received. Our team is reviewing your requirements and will contact you shortly.
                              </p>
                            </td>
                          </tr>

                          <!-- 3. CARD 1: YOUR INQUIRY SUMMARY (Width: 562px) -->
                          <tr>
                            <td style="padding:6px 60px 22px;background:#ffffff">
                              <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border:1.5px solid #0052a5;border-radius:8px;overflow:hidden;box-shadow:0 3px 12px rgba(0,82,165,0.06)">
                                <tr>
                                  <td colspan="2" style="background:#0052a5;padding:11px 22px;border-bottom:1px solid #004593">
                                    <span style="font-size:12px;font-weight:800;letter-spacing:1.2px;color:#ffffff;text-transform:uppercase">
                                      YOUR INQUIRY SUMMARY
                                    </span>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:13px 22px;font-size:13.5px;color:#475569;border-bottom:1px solid #e2e8f0;width:38%%">Reference Number</td>
                                  <td style="padding:13px 22px;font-size:14px;font-weight:800;color:#0f172a;border-bottom:1px solid #e2e8f0">%s</td>
                                </tr>
                                <tr>
                                  <td style="padding:13px 22px;font-size:13.5px;color:#475569;border-bottom:1px solid #e2e8f0">Mobile Number</td>
                                  <td style="padding:13px 22px;font-size:14px;font-weight:800;color:#0f172a;border-bottom:1px solid #e2e8f0">%s</td>
                                </tr>
                                <tr>
                                  <td style="padding:13px 22px;font-size:13.5px;color:#475569;border-bottom:1px solid #e2e8f0">Service</td>
                                  <td style="padding:13px 22px;font-size:14px;font-weight:800;color:#0f172a;border-bottom:1px solid #e2e8f0">%s</td>
                                </tr>
                                <tr>
                                  <td style="padding:13px 22px;font-size:13.5px;color:#475569;border-bottom:1px solid #e2e8f0">Customer Type</td>
                                  <td style="padding:13px 22px;font-size:14px;font-weight:800;color:#0f172a;border-bottom:1px solid #e2e8f0">%s</td>
                                </tr>
                                <tr>
                                  <td style="padding:13px 22px;font-size:13.5px;color:#475569;border-bottom:1px solid #e2e8f0">Your Requirements</td>
                                  <td style="padding:13px 22px;font-size:13.5px;color:#1e293b;border-bottom:1px solid #e2e8f0;line-height:1.5">%s</td>
                                </tr>
                                <tr>
                                  <td style="padding:13px 22px;font-size:13.5px;color:#475569">Status</td>
                                  <td style="padding:13px 22px">
                                    <span style="display:inline-block;background:#cbf542;color:#1d3800;font-size:11px;font-weight:800;padding:5px 14px;border-radius:20px;text-transform:uppercase;letter-spacing:0.5px">
                                      INQUIRY RECEIVED
                                    </span>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <!-- 4. CARD 2: WHAT HAPPENS NEXT? (Width: 562px) -->
                          <tr>
                            <td style="padding:6px 60px 24px;background:#ffffff">
                              <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border:1.5px solid #0052a5;border-radius:8px;overflow:hidden;box-shadow:0 3px 12px rgba(0,82,165,0.06)">
                                <tr>
                                  <td style="background:#0052a5;padding:11px 22px;border-bottom:1px solid #004593">
                                    <span style="font-size:12px;font-weight:800;letter-spacing:1.2px;color:#ffffff;text-transform:uppercase">
                                      WHAT HAPPENS NEXT?
                                    </span>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:16px 22px">
                                    <table width="100%%" cellpadding="0" cellspacing="0">
                                      <!-- Step 01 -->
                                      <tr>
                                        <td style="padding:10px 0;border-bottom:1px solid #e2e8f0;vertical-align:top">
                                          <table cellpadding="0" cellspacing="0"><tr>
                                            <td style="width:36px;height:36px;background:#0052a5;border-radius:50%%;text-align:center;vertical-align:middle;font-size:12px;font-weight:800;color:#ffffff;min-width:36px">01</td>
                                            <td style="padding-left:14px">
                                              <p style="margin:0;font-size:14px;font-weight:800;color:#0f172a">Requirement Review</p>
                                              <p style="margin:2px 0 0;font-size:13px;color:#475569">Our team reviews your solar installation requirements.</p>
                                            </td>
                                          </tr></table>
                                        </td>
                                      </tr>
                                      <!-- Step 02 -->
                                      <tr>
                                        <td style="padding:12px 0;border-bottom:1px solid #e2e8f0;vertical-align:top">
                                          <table cellpadding="0" cellspacing="0"><tr>
                                            <td style="width:36px;height:36px;background:#0052a5;border-radius:50%%;text-align:center;vertical-align:middle;font-size:12px;font-weight:800;color:#ffffff;min-width:36px">02</td>
                                            <td style="padding-left:14px">
                                              <p style="margin:0;font-size:14px;font-weight:800;color:#0f172a">Consultation</p>
                                              <p style="margin:2px 0 0;font-size:13px;color:#475569">We contact you to understand your property and energy needs.</p>
                                            </td>
                                          </tr></table>
                                        </td>
                                      </tr>
                                      <!-- Step 03 -->
                                      <tr>
                                        <td style="padding:12px 0;border-bottom:1px solid #e2e8f0;vertical-align:top">
                                          <table cellpadding="0" cellspacing="0"><tr>
                                            <td style="width:36px;height:36px;background:#0052a5;border-radius:50%%;text-align:center;vertical-align:middle;font-size:12px;font-weight:800;color:#ffffff;min-width:36px">03</td>
                                            <td style="padding-left:14px">
                                              <p style="margin:0;font-size:14px;font-weight:800;color:#0f172a">Customized Quotation</p>
                                              <p style="margin:2px 0 0;font-size:13px;color:#475569">We prepare a proposal tailored to your requirements.</p>
                                            </td>
                                          </tr></table>
                                        </td>
                                      </tr>
                                      <!-- Step 04 -->
                                      <tr>
                                        <td style="padding:12px 0 4px;vertical-align:top">
                                          <table cellpadding="0" cellspacing="0"><tr>
                                            <td style="width:36px;height:36px;background:#0052a5;border-radius:50%%;text-align:center;vertical-align:middle;font-size:12px;font-weight:800;color:#ffffff;min-width:36px">04</td>
                                            <td style="padding-left:14px">
                                              <p style="margin:0;font-size:14px;font-weight:800;color:#0f172a">Installation Planning</p>
                                              <p style="margin:2px 0 0;font-size:13px;color:#475569">Once you approve the proposal, we discuss the next steps.</p>
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

                          <!-- 5. CLOSING LINE -->
                          <tr>
                            <td style="padding:0 60px 28px;background:#ffffff">
                              <p style="margin:0;font-size:14.5px;color:#334155;line-height:1.75">
                                Thank you for giving us the opportunity to support your transition to clean energy.
                              </p>
                            </td>
                          </tr>

                          <!-- 6. FOOTER (Bulletproof pure HTML table with clickable contact links matching design) -->
                          <tr>
                            <td style="padding:22px 20px 24px;background:#0052a5;text-align:center;border-bottom-left-radius:14px;border-bottom-right-radius:14px">
                              <p style="margin:0 0 4px;font-size:16px;font-weight:700;color:#ffffff;font-family:Arial,sans-serif;letter-spacing:0.3px">
                                TAM Solar Solutions
                              </p>
                              <p style="margin:0 0 12px;font-size:13px;color:#dbeafe;font-family:Arial,sans-serif">
                                Customer Support Team
                              </p>
                              <table cellpadding="0" cellspacing="0" style="margin:0 auto">
                                <tr>
                                  <td style="padding:0 10px;font-size:13px;color:#ffffff;font-family:Arial,sans-serif">
                                    &#9993;&nbsp;<a href="mailto:solarsolutionstam@gmail.com" style="color:#ffffff;text-decoration:none;font-weight:600">solarsolutionstam@gmail.com</a>
                                  </td>
                                  <td style="color:#93c5fd;font-size:14px;padding:0 4px">&#8226;</td>
                                  <td style="padding:0 10px;font-size:13px;color:#ffffff;font-family:Arial,sans-serif">
                                    &#9742;&nbsp;<a href="tel:+918383063590" style="color:#ffffff;text-decoration:none;font-weight:600">+91 83830 63590</a>
                                  </td>
                                </tr>
                              </table>
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
                escape(phone),
                escape(enquiryType),
                escape(customerType),
                escapeWithLineBreaks(message));

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
                name, customerEmail, phone, customerType, enquiryType,
                submittedAt, message, reference, adminUrl);

        String adminHtml = """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background:#f0f2ef;font-family:Arial,sans-serif;color:#16251f">
                  <table width="100%%" cellpadding="0" cellspacing="0"
                         style="background:#f0f2ef;padding:32px 0">
                    <tr><td align="center">
                      <table width="620" cellpadding="0" cellspacing="0"
                             style="background:#fff;border-radius:10px;overflow:hidden;
                                    box-shadow:0 4px 20px rgba(0,0,0,0.08);max-width:620px;border:1px solid #e4e8df">
                        <tr>
                          <td style="background:#1b5299;padding:26px 40px;border-bottom:3px solid #38bdf8">
                            <table width="100%%"><tr>
                              <td>
                                <p style="margin:0;font-size:20px;font-weight:700;color:#ffffff">
                                  TAM Solar Solutions</p>
                                <p style="margin:4px 0 0;font-size:10px;
                                          color:#e2eaf4;letter-spacing:2px;
                                          text-transform:uppercase">Admin Notification</p>
                              </td>
                              <td align="right">
                                <span style="background:rgba(255,255,255,0.2);color:#ffffff;
                                             font-size:10px;font-weight:700;padding:5px 14px;
                                             border-radius:20px;
                                             border:1px solid rgba(255,255,255,0.4)">
                                  NEW INQUIRY</span>
                              </td>
                            </tr></table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 40px 0;background:#ffffff">
                            <p style="margin:0 0 6px;font-size:17px;font-weight:700;color:#16251f">
                              New Inquiry from %s
                            </p>
                            <p style="margin:0 0 24px;font-size:13px;color:#68736c;line-height:1.6">
                              A new customer inquiry has been received through the TAM Solar Solutions website.
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:0 40px 20px;background:#ffffff">
                            <table width="100%%" cellpadding="0" cellspacing="0"
                                   style="background:#f8faf6;border-radius:8px;border:1px solid #e4e8df;overflow:hidden">
                              <tr>
                                <td colspan="2"
                                    style="background:#1b5299;padding:10px 16px;
                                           font-size:11px;font-weight:700;color:#ffffff;
                                           letter-spacing:1px;text-transform:uppercase">
                                  Customer Overview
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:10px 16px;font-size:13px;color:#68736c;
                                           border-bottom:1px solid #eef1ea;width:38%%">Name</td>
                                <td style="padding:10px 16px;font-size:13px;font-weight:700;
                                           color:#16251f;border-bottom:1px solid #eef1ea">%s</td>
                              </tr>
                              <tr>
                                <td style="padding:10px 16px;font-size:13px;color:#68736c;
                                           border-bottom:1px solid #eef1ea">Email</td>
                                <td style="padding:10px 16px;font-size:13px;font-weight:700;
                                           color:#16251f;border-bottom:1px solid #eef1ea">
                                  <a href="mailto:%s" style="color:#1b5299;text-decoration:none">%s</a>
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:10px 16px;font-size:13px;color:#68736c;
                                           border-bottom:1px solid #eef1ea">Mobile Number</td>
                                <td style="padding:10px 16px;font-size:13px;font-weight:700;
                                           color:#16251f;border-bottom:1px solid #eef1ea">
                                  <a href="tel:%s" style="color:#1b5299;text-decoration:none">%s</a>
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:10px 16px;font-size:13px;color:#68736c;
                                           border-bottom:1px solid #eef1ea">Customer Type</td>
                                <td style="padding:10px 16px;font-size:13px;color:#16251f;
                                           border-bottom:1px solid #eef1ea">%s</td>
                              </tr>
                              <tr>
                                <td style="padding:10px 16px;font-size:13px;color:#68736c;
                                           border-bottom:1px solid #eef1ea">Service</td>
                                <td style="padding:10px 16px;font-size:13px;font-weight:700;
                                           color:#16251f;border-bottom:1px solid #eef1ea">%s</td>
                              </tr>
                              <tr>
                                <td style="padding:10px 16px;font-size:13px;color:#68736c">Submitted At</td>
                                <td style="padding:10px 16px;font-size:13px;color:#16251f">%s</td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:0 40px 22px;background:#ffffff">
                            <p style="margin:0 0 8px;font-size:11px;font-weight:700;color:#68736c;
                                      letter-spacing:1px;text-transform:uppercase">
                              Customer Requirements
                            </p>
                            <div style="background:#f0f7ff;border-left:4px solid #1b5299;
                                        padding:14px 18px;border-radius:0 6px 6px 0;border:1px solid #dbeafe;border-left-width:4px">
                              <p style="margin:0;font-size:13px;color:#16251f;line-height:1.7;
                                        font-style:italic">
                                "%s"
                              </p>
                            </div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:0 40px 26px;background:#ffffff">
                            <table width="100%%" cellpadding="0" cellspacing="0"
                                   style="background:#f8faf6;border:1px solid #e4e8df;border-radius:8px;padding:16px 20px">
                              <tr>
                                <td>
                                  <p style="margin:0 0 10px;font-size:11px;font-weight:700;color:#1b5299;
                                            letter-spacing:1px;text-transform:uppercase">
                                    Next Action Steps
                                  </p>
                                  <table width="100%%" cellpadding="3" cellspacing="0"
                                         style="font-size:12px;color:#374151">
                                    <tr><td style="color:#1b5299;width:20px;font-weight:700">1.</td>
                                        <td>Review the customer requirements above</td></tr>
                                    <tr><td style="color:#1b5299;font-weight:700">2.</td>
                                        <td>Contact customer via email or phone</td></tr>
                                    <tr><td style="color:#1b5299;font-weight:700">3.</td>
                                        <td>Prepare and dispatch a solar quotation</td></tr>
                                    <tr><td style="color:#1b5299;font-weight:700">4.</td>
                                        <td>Update inquiry status in the admin panel</td></tr>
                                  </table>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:0 40px 30px;background:#ffffff" align="center">
                            <a href="%s"
                               style="display:inline-block;background:#1b5299;color:#ffffff;
                                      font-size:13px;font-weight:700;padding:12px 28px;
                                      border-radius:4px;text-decoration:none;letter-spacing:0.5px">
                              Open in Admin Panel &rarr;
                            </a>
                            <p style="margin:10px 0 0;font-size:11px;color:#68736c">
                              Reference: <strong style="color:#16251f">%s</strong>
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="background:#1b5299;padding:18px 40px;border-top:1px solid rgba(255,255,255,0.15);
                                     text-align:center">
                            <p style="margin:0;font-size:11px;color:#ffffff">
                              This is an automated notification from the TAM Solar Solutions website.
                            </p>
                            <p style="margin:3px 0 0;font-size:11px;color:#e2eaf4">
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
                escape(name),
                escape(customerEmail), escape(customerEmail),
                escape(phone), escape(phone),
                escape(customerType),
                escape(enquiryType),
                escape(submittedAt),
                escapeWithLineBreaks(message),
                escape(adminUrl),
                escape(reference));

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
                <body style="margin:0;padding:24px;background:#071a2f;font-family:Arial,sans-serif;color:#ffffff;line-height:1.7">
                    <div style="background:#0f2b48;padding:28px 32px;border-radius:10px;border:1px solid #1a3d60;color:#ffffff;max-width:600px">
                        <div style="font-size:15px;color:#ffffff">%s</div>
                        <hr style="border:none;border-top:1px solid #1a3d60;margin:24px 0 16px">
                        <p style="color:#93c5fd;font-size:12px;margin:0 0 8px">
                            Enquiry reference: <strong style="color:#ffffff">%s</strong>
                        </p>
                        <p style="color:#cbd5e1;font-size:13px;margin:0">
                            Regards,<br><strong style="color:#38bdf8">TAM Solar Solutions Team</strong>
                        </p>
                    </div>
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
    @Scheduled(fixedDelayString = "${tamsolar.email.retry-delay-ms:5000}")
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

        if (message.htmlBody() != null) {
            if (message.htmlBody().contains("cid:emailHeader")) {
                Resource headerRes = findResource("static/assets/images/email-header.png", "frontend/assets/images/email-header.png");
                if (headerRes != null && headerRes.exists()) {
                    helper.addInline("emailHeader", headerRes, "image/png");
                }
            }
            if (message.htmlBody().contains("cid:emailFooter")) {
                Resource footerRes = findResource("static/assets/images/email-footer.png", "frontend/assets/images/email-footer.png");
                if (footerRes != null && footerRes.exists()) {
                    helper.addInline("emailFooter", footerRes, "image/png");
                }
            }
            if (message.htmlBody().contains("cid:tamLogo")) {
                Resource logoRes = findResource("static/assets/images/tam-solar-logo.jpg", "frontend/assets/images/tam-solar-logo.jpg");
                if (logoRes != null && logoRes.exists()) {
                    helper.addInline("tamLogo", logoRes, "image/jpeg");
                }
            }
        }

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
            String domain = from.substring(from.lastIndexOf('@') + 1);
            messageId = "<tam-solar-" + message.id() + "@" + domain + ">";
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

    private Resource findResource(String classpathLocation, String fallbackFilePath) {
        Resource resource = new ClassPathResource(classpathLocation);
        if (resource.exists()) {
            return resource;
        }
        // Fallback without subfolders
        String fileName = fallbackFilePath.substring(fallbackFilePath.lastIndexOf('/') + 1);
        resource = new ClassPathResource("static/" + fileName);
        if (resource.exists()) {
            return resource;
        }
        resource = new FileSystemResource(fallbackFilePath);
        if (resource.exists()) {
            return resource;
        }
        resource = new FileSystemResource("../" + fallbackFilePath);
        if (resource.exists()) {
            return resource;
        }
        resource = new FileSystemResource("frontend/" + fileName);
        if (resource.exists()) {
            return resource;
        }
        return null;
    }
}
