package com.example.lunastreaming.service;

import com.example.lunastreaming.model.entity.OtpVerificationEntity;
import com.example.lunastreaming.repository.OtpVerificationRepository;
import com.example.lunastreaming.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Random;

@Service
public class OtpService {

    private final OtpVerificationRepository otpRepository;
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;

    private final String nodeServiceUrl;
    private final String instanceId;

    // Inyección limpia por constructor de dependencias y propiedades de configuración
    public OtpService(
            OtpVerificationRepository otpRepository,
            @Value("${whatsapp.service-url}") String nodeServiceUrl,
            @Value("${whatsapp.instance-id}") String instanceId,
            UserRepository userRepository) {
        this.otpRepository = otpRepository;
        this.nodeServiceUrl = nodeServiceUrl;
        this.instanceId = instanceId;
        this.restTemplate = new RestTemplate();
        this.userRepository = userRepository;// Inicialización limpia del cliente HTTP
    }

    /**
     * Solicita la generación de un OTP, aplica Rate Limit y delega el envío asíncrono.
     */
    @Transactional(readOnly = false)
    public void solicitarOtp(String telefonoRaw) {
        // 1. Normalizar el teléfono para el Microservicio de Node y el Rate Limit (Solo dígitos)
        // Ej: Si viene "+51 999 888 777" o "+51999888777", lo convierte en "51999888777"
        String telefonoSoloDigitos = telefonoRaw.replaceAll("\\D", "");

        // 2. Normalizar el teléfono con el prefijo '+' para tu 'userRepository' de Luna Streaming
        // Ej: Se convierte estrictamente en "+51999888777"
        String telefonoFormatoBaseDatos = "+" + telefonoSoloDigitos;

        // 3. NUEVA VALIDACIÓN UNIFICADA: Validar contra tu lógica de 'phone_taken'
        if (userRepository.findByPhone(telefonoFormatoBaseDatos).isPresent()) {
            throw new IllegalArgumentException("El número de teléfono ya está registrado.");
        }

        // 4. Validar el Rate Limit de 60 segundos por número de teléfono (usando solo dígitos)
        if (otpRepository.existsActiveRateLimit(telefonoSoloDigitos)) {
            throw new IllegalStateException("Por favor, espera 60 segundos antes de solicitar otro código.");
        }

        // 5. Generar código numérico aleatorio de 6 dígitos
        String codigo = String.format("%06d", new Random().nextInt(999999));
        String hash = hashSha256(codigo);

        // 6. Crear y persistir el registro del OTP (usando la clave limpia)
        OtpVerificationEntity otp = new OtpVerificationEntity();
        otp.setTelefono(telefonoSoloDigitos); // Se almacena indexado sin símbolos
        otp.setCodigoHash(hash);
        otp.setUltimoEnvioAt(OffsetDateTime.now());
        otp.setExpiraAt(OffsetDateTime.now().plusMinutes(5));
        otp.setIntentosValidantes(0);
        otp.setMaxIntentosPermitidos(3);
        otp.setUtilizado(false);
        otp.setCreatedAt(OffsetDateTime.now());

        otpRepository.save(otp);

        // 7. Delegar de forma asíncrona el envío al microservicio de Node.js
        enviarMensajeWhatsAppAsync(telefonoSoloDigitos, codigo);
    }

    /**
     * Valida el código OTP presentado por el usuario contra el último hash activo.
     */
    @Transactional
    public boolean verificarOtp(String telefono, String codigoPresentado) {
        // 1. Buscar el último OTP vigente e indexado
        OtpVerificationEntity otp = otpRepository.findLatestActiveOtp(telefono)
                .orElseThrow(() -> new RuntimeException("El código ha expirado o no existe una solicitud activa."));

        // 2. Validar que no haya excedido el límite de intentos permitidos
        if (otp.getIntentosValidantes() >= otp.getMaxIntentosPermitidos()) {
            throw new RuntimeException("Has excedido el número máximo de intentos. Solicita un código nuevo.");
        }

        // 3. Comparar Hashes (Código presentado vs Almacenado)
        String hashPresentado = hashSha256(codigoPresentado);
        if (!otp.getCodigoHash().equals(hashPresentado)) {
            // Incrementamos usando los métodos generados por Lombok
            otp.setIntentosValidantes(otp.getIntentosValidantes() + 1);
            otpRepository.save(otp);
            throw new RuntimeException("Código de verificación incorrecto.");
        }

        // 4. Quemar código si la verificación fue exitosa para que no se use dos veces
        otp.setUtilizado(true);
        otpRepository.save(otp);
        return true;
    }

    /**
     * Despacha la solicitud HTTP al Sidecar de Node.js en un hilo secundario de forma asíncrona.
     */
    @Async
    protected void enviarMensajeWhatsAppAsync(String telefono, String codigo) {
        try {
            Map<String, String> request = Map.of(
                    "instanceId", this.instanceId,
                    "phone", telefono,
                    "code", codigo
            );

            // Envío por POST al microservicio local o productivo
            restTemplate.postForEntity(this.nodeServiceUrl, request, Map.class);

        } catch (Exception e) {
            // Fallback de logs seguro para evitar caídas del flujo principal
            System.err.println("Fallo crítico al despachar OTP vía Node.js: " + e.getMessage());
        }
    }

    /**
     * Utilitario criptográfico para cifrar en SHA-256 el OTP en texto plano.
     */
    private String hashSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes("UTF-8"));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error procesando seguridad del código.");
        }
    }
}
