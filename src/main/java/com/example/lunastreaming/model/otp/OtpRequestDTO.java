package com.example.lunastreaming.model.otp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpRequestDTO(

        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = "^[0-9]{11,15}$", message = "Formato de teléfono inválido (debe incluir código de país sin '+')")
        String telefono
) {
}
