package com.example.lunastreaming.service;

import com.example.lunastreaming.model.entity.OtpVerificationEntity;
import com.example.lunastreaming.model.otp.OtpContext;
import com.example.lunastreaming.repository.OtpVerificationRepository;
import com.example.lunastreaming.repository.UserRepository;

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
        this.solicitarOtp(telefonoRaw, OtpContext.REGISTER_SELLER);
    }

    /**
     * Solicita la generación de un OTP basado en un contexto específico del sistema.
     */
    @Transactional(readOnly = false)
    public void solicitarOtp(String telefonoRaw, OtpContext contexto) {
        String telefonoSoloDigitos = telefonoRaw.replaceAll("\\D", "");
        String telefonoFormatoBaseDatos = "+" + telefonoSoloDigitos;

        // Ojo: Si es recuperación de contraseña o cambio de teléfono, la lógica cambia.
        // Solo debes rebotar si el teléfono está registrado CUANDO es un registro nuevo.
        if (contexto == OtpContext.REGISTER_SELLER || contexto == OtpContext.REGISTER_PROVIDER) {
            if (userRepository.findByPhone(telefonoFormatoBaseDatos).isPresent()) {
                throw new IllegalArgumentException("El número de teléfono ya está registrado.");
            }
        }

        // Si fuera PASSWORD_RESET, más bien podrías validar que el teléfono SÍ exista:
        // if (contexto == OtpContext.PASSWORD_RESET && userRepository.findByPhone(telefonoFormatoBaseDatos).isEmpty()) { ... }

        // Validar Rate Limit de 60 segundos
        if (otpRepository.existsActiveRateLimit(telefonoSoloDigitos)) {
            throw new IllegalStateException("Por favor, espera 60 segundos antes de solicitar otro código.");
        }

        String codigo = String.format("%06d", new Random().nextInt(999999));
        String hash = hashSha256(codigo);

        OtpVerificationEntity otp = new OtpVerificationEntity();
        otp.setTelefono(telefonoSoloDigitos);
        otp.setCodigoHash(hash);
        otp.setUltimoEnvioAt(OffsetDateTime.now());
        otp.setExpiraAt(OffsetDateTime.now().plusMinutes(5));
        otp.setIntentosValidantes(0);
        otp.setMaxIntentosPermitidos(3);
        otp.setUtilizado(false);
        otp.setCreatedAt(OffsetDateTime.now());

        otpRepository.save(otp);

        // Pasamos el contexto al despachador asíncrono
        enviarMensajeWhatsAppAsync(telefonoSoloDigitos, codigo, contexto);
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
    protected void enviarMensajeWhatsAppAsync(String telefono, String codigo, OtpContext contexto) {
        try {
            // Evaluamos de forma estricta e inyectamos la plantilla correspondiente
            String mensajeFinal = switch (contexto) {
                case REGISTER_SELLER ->
                        "🚀 *Luna Plataformas — Registro*\n\n" +
                                "Tu código de verificación es: * " + codigo + " *.\n" +
                                "Expirará en 5 minutos.";

                case REGISTER_PROVIDER ->
                        "🔑 *Luna Plataformas — Registro de Proveedor*\n\n" +
                                "Tu código de verificación es: * " + codigo + " *\n\n" +
                                "Por seguridad, no compartas este código con nadie.";

                case PASSWORD_RESET ->
                        "🔒 *Luna Streaming — Recuperación de Contraseña*\n\n" +
                                "Has solicitado restablecer tu contraseña. Ingresa el código: * " + codigo + " *.\n\n" +
                                "Si no solicitaste esto, ignora este mensaje.";

                case CHANGE_PHONE ->
                        "📱 *Luna Streaming — Cambio de Celular*\n\n" +
                                "Código de confirmación para vincular este número de WhatsApp: * " + codigo + " *.";

                case SENSITIVE_TRANSACTION ->
                        "⚠️ *Luna Streaming — Operación Crítica*\n\n" +
                                "Código temporal para autorizar tu solicitud: * " + codigo + " *.";
            };

            Map<String, String> request = Map.of(
                    "instanceId", this.instanceId,
                    "phone", telefono,
                    "code", codigo,
                    "message", mensajeFinal // El microservicio de Render tomará el string ya formateado
            );

            restTemplate.postForEntity(this.nodeServiceUrl, request, Map.class);

        } catch (Exception e) {
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
