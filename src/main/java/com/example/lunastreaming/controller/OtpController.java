package com.example.lunastreaming.controller;

import com.example.lunastreaming.model.otp.OtpRequestDTO;
import com.example.lunastreaming.model.otp.OtpValidationDTO;
import com.example.lunastreaming.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/solicitar")
    public ResponseEntity<?> solicitarOtp(@Valid @RequestBody OtpRequestDTO request) {
        try {
            otpService.solicitarOtp(request.telefono(), request.contexto());
            return ResponseEntity.ok(Map.of("success", true, "message", "Código OTP enviado con éxito."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno al procesar la solicitud."));
        }
    }

    @PostMapping("/verificar")
    public ResponseEntity<?> verificarOtp(@Valid @RequestBody OtpValidationDTO request) {
        try {
            boolean isValid = otpService.verificarOtp(request.telefono(), request.codigo());
            if (isValid) {
                // Aquí procederías a realizar el login, activar la cuenta, o retornar tu JWT Token
                return ResponseEntity.ok(Map.of("success", true, "message", "Validación exitosa. Acceso concedido."));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Código incorrecto."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
}
