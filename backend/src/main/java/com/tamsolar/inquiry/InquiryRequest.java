package com.tamsolar.inquiry;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class InquiryRequest {

    @NotBlank(message = "Enter your name.")
    @Size(max = 100, message = "Name must be 100 characters or fewer.")
    private String name;

    @NotBlank(message = "Enter your email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 254, message = "Email must be 254 characters or fewer.")
    private String email;

    @NotBlank(message = "Choose a customer type.")
    @Pattern(
        regexp = "Homeowner|Business|Retailer|Wholesaler|Installer|Other",
        message = "Choose a valid customer type."
    )
    private String customerType;

    @NotBlank(message = "Choose an enquiry topic.")
    @Pattern(
        regexp = "Solar quote|Product information|Trade partnership|Customer support|Order support|Warranty enquiry|Other",
        message = "Choose a valid enquiry topic."
    )
    private String enquiryType;

    @NotBlank(message = "Enter a message.")
    @Size(max = 5000, message = "Message must be 5,000 characters or fewer.")
    private String message;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getEnquiryType() {
        return enquiryType;
    }

    public void setEnquiryType(String enquiryType) {
        this.enquiryType = enquiryType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
