package com.example.lunastreaming.service;

import com.example.lunastreaming.builder.SettingBuilder;
import com.example.lunastreaming.model.SettingEntity;
import com.example.lunastreaming.model.SettingRequest;
import com.example.lunastreaming.model.SettingResponse;
import com.example.lunastreaming.repository.SettingRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SettingService {

    // simple cache in-memory (optional)
    private final Map<String, SettingEntity> cache = new ConcurrentHashMap<>();


    private final SettingRepository settingRepository;
    private final SettingBuilder settingBuilder;

    public BigDecimal getNumber(String key, BigDecimal fallback) {
        SettingEntity s = getCached(key);
        if (s == null) return fallback;
        if (!"number".equalsIgnoreCase(s.getType()) || s.getValueNum() == null) return fallback;
        return s.getValueNum();
    }

    public SettingEntity getSetting(String key) {
        return getCached(key);
    }


    @Transactional
    public SettingResponse updateSetting(String key, SettingRequest request, UUID adminId) {
        SettingEntity s = settingRepository.findByKeyIgnoreCase(key)
                .orElseThrow(() -> new EntityNotFoundException("Setting " + key + " not found"));

        // Lógica dinámica según el tipo de configuración
        if ("boolean".equalsIgnoreCase(s.getType())) {
            if (request.getValueBool() == null) {
                throw new IllegalArgumentException("Setting " + key + " requires a boolean value");
            }
            s.setValueBool(request.getValueBool());
        } else if ("number".equalsIgnoreCase(s.getType())) {
            if (request.getNumber() == null) {
                throw new IllegalArgumentException("Setting " + key + " is numeric and requires valueNum");
            }
            s.setValueNum(request.getNumber());
        } else {
            throw new IllegalArgumentException("Unsupported setting type: " + s.getType());
        }

        s.setUpdatedAt(Instant.now());
        // Opcional: buscar el admin y asignarlo a s.setUpdatedBy(...)

        SettingEntity saved = settingRepository.save(s);
        cache.put(key.toLowerCase(Locale.ROOT), saved);

        return settingBuilder.toSettingResponse(saved);
    }

    private SettingEntity getCached(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        SettingEntity s = cache.get(k);
        if (s != null && s.getUpdatedAt() != null && s.getUpdatedAt().isAfter(Instant.now().minus(5, ChronoUnit.MINUTES))) {
            return s;
        }
        return settingRepository.findByKeyIgnoreCase(key).map(it -> { cache.put(k, it); return it; }).orElse(null);
    }

    public List<SettingResponse> getSettings() {
        return settingRepository.findAll()
                .stream().map(settingBuilder::toSettingResponse)
                .toList();
    }

    public void saveSetting() {
        SettingEntity supplierDiscount = SettingEntity.builder()
                .key("supplierDiscount")
                .type("number")
                .valueNum(new BigDecimal("0.10"))      // 0.10 = 10%
                .description("Descuento por proveedor (por ejemplo 0.10 = 10%)")
                .updatedAt(Instant.now())
                .build();

        SettingEntity supplierPublication = SettingEntity.builder()
                .key("supplierPublication")
                .type("number")
                .valueNum(new BigDecimal("15"))        // costo/valor por publicación
                .description("Costo/valor por publicación para proveedores")
                .updatedAt(Instant.now())
                .build();

        settingRepository.save(supplierDiscount);
        settingRepository.save(supplierPublication);
    }

}
