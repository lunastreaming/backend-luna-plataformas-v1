package com.example.lunastreaming.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "El usuario es obligatorio")
    @Size(min = 4, max = 20, message = "El usuario debe tener entre 4 y 20 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "El usuario solo puede contener letras y números")
    public String username;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 100) // Evita ataques de denegación de servicio por contraseñas gigantes
    public String password;

}
