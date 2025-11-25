package com.example.lunastreaming.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminChangePasswordRequest {

    @NotBlank
    @Size(min = 8, max = 100)
    private String newPassword;

    // Opcional: obliga al usuario a cambiar su password en el próximo login
    private Boolean requireReset = Boolean.FALSE;

}
