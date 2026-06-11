package com.example.lunastreaming.model.otp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OtpRequestDTO(

        @NotBlank(message = "El teléfono es obligatorio")
        String telefono,

        @NotNull(message = "El contexto de la solicitud es obligatorio")
        OtpContext contexto // Se mapeará automáticamente desde el JSON (ej: "REGISTER_PROVIDER")
) {
}
