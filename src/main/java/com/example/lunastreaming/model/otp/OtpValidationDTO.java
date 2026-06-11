package com.example.lunastreaming.model.otp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OtpValidationDTO(
        @NotBlank String telefono,
        @NotBlank @Size(min = 6, max = 6) String codigo
) {
}
