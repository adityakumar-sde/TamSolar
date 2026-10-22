package com.tamsolar.inquiry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AdminStatusRequest {

    @NotBlank
    @Pattern(regexp = "NEW|IN_PROGRESS|REPLIED|CONVERTED|CLOSED")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
