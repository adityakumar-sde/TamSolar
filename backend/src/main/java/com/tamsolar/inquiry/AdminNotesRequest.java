package com.tamsolar.inquiry;

import jakarta.validation.constraints.Size;

public class AdminNotesRequest {

    @Size(max = 10000, message = "Internal notes must be 10,000 characters or fewer.")
    private String notes;

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
