package com.tamsolar.inquiry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AdminReplyRequest {

    @NotBlank(message = "Enter an email subject.")
    @Size(max = 300, message = "Subject must be 300 characters or fewer.")
    @Pattern(regexp = "^[^\\r\\n]+$", message = "Subject must be a single line.")
    private String subject;

    @NotBlank(message = "Enter a reply message.")
    @Size(max = 10000, message = "Reply must be 10,000 characters or fewer.")
    private String message;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
