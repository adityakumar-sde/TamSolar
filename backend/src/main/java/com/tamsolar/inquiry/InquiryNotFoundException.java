package com.tamsolar.inquiry;

public class InquiryNotFoundException extends RuntimeException {

    public InquiryNotFoundException(long id) {
        super("Enquiry " + id + " was not found.");
    }
}
