package com.example.lunastreaming.builder;

import com.example.lunastreaming.model.StockEntity;
import com.example.lunastreaming.model.StockResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;


@Component
public class StockBuilder {

    public StockResponse toStockResponse(StockEntity stockEntity) {
        if (stockEntity == null) return null;

        UUID productId = null;
        String productName = null;
        Boolean renewable = null;
        BigDecimal renewalPrice = stockEntity.getPurchasePrice();
        if (stockEntity.getProduct() != null) {
            productId = stockEntity.getProduct().getId();
            productName = stockEntity.getProduct().getName();
            renewable = stockEntity.getProduct().getIsRenewable();
            renewalPrice = stockEntity.getProduct().getRenewalPrice();
        }

        UUID buyerId = null;
        String buyerUsername = null;
        String buyerUsernamePhone = null;
        if (stockEntity.getBuyer() != null) {
            buyerId = stockEntity.getBuyer().getId();
            buyerUsername = stockEntity.getBuyer().getUsername();
            buyerUsernamePhone = stockEntity.getBuyer().getPhone();
        }

        Instant soldAtInstant = null;
        if (stockEntity.getSoldAt() != null) {
            soldAtInstant = stockEntity.getSoldAt().toInstant();
        }

        BigDecimal refund = ZERO;

        if (stockEntity.getEndAt() != null) {
            BigDecimal productPrice = stockEntity.getPurchasePrice() != null ? stockEntity.getPurchasePrice() : null;
            Integer productDays = stockEntity.getProduct() != null ? stockEntity.getProduct().getDays() : null;
            refund = computeRefund(productPrice, productPrice, productDays, stockEntity.getEndAt(), BigDecimal.ZERO, stockEntity.getStartAt());
        }

        // calcular daysRemaining y daysPublished
        Integer daysRemaining = null;
        if (stockEntity.getEndAt() != null) {
            daysRemaining = computeDaysBetween(Instant.now(), stockEntity.getEndAt(), true);
        }

        return StockResponse
                .builder()
                .id(stockEntity.getId())
                .productId(productId)
                .productName(productName)
                .username(stockEntity.getUsername())
                .password(stockEntity.getPassword())
                .url(stockEntity.getUrl())
                .type(stockEntity.getTipo())
                .numberProfile(stockEntity.getNumeroPerfil())
                .status(stockEntity.getStatus())
                .pin(stockEntity.getPin())
                // nuevos campos
                .soldAt(soldAtInstant)
                .buyerId(buyerId)
                .buyerUsername(buyerUsername)
                .buyerUsernamePhone(buyerUsernamePhone)
                .clientName(stockEntity.getClientName())
                .clientPhone(stockEntity.getClientPhone())
                .startAt(stockEntity.getStartAt())
                .endAt(stockEntity.getEndAt())
                .daysRemaining(daysRemaining)
                .refund(refund)
                .amount(stockEntity.getProduct().getSalePrice())
                .purchasePrice(stockEntity.getPurchasePrice())
                .renewable(renewable)
                .renewalPrice(renewalPrice)
                .build();
    }

    public StockEntity fromStockResponse(StockResponse stockResponse) {
        if (stockResponse == null) return null;

        StockEntity.StockEntityBuilder builder = StockEntity.builder()
                .username(stockResponse.getUsername())
                .password(stockResponse.getPassword())
                .url(stockResponse.getUrl())
                .tipo(stockResponse.getType())
                .numeroPerfil(stockResponse.getNumberProfile())
                .status(stockResponse.getStatus() != null ? stockResponse.getStatus() : "inactive")
                .pin(stockResponse.getPin());


        StockEntity entity = builder.build();

        // setear campos adicionales no contemplados en el builder (si tu builder no incluye estos)
        if (stockResponse.getSoldAt() != null) {
            entity.setSoldAt(Timestamp.from(stockResponse.getSoldAt()));
        }

        entity.setClientName(stockResponse.getClientName());
        entity.setClientPhone(stockResponse.getClientPhone());

        return entity;
    }

    private static final long SECONDS_PER_DAY = 86_400L;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public BigDecimal computeRefund(
            final BigDecimal paidAmount,
            final BigDecimal productPrice,
            final Integer productDays,
            final Instant endAt,
            final BigDecimal feePercent,
            final Instant startAt // 👈 necesitas también la fecha de inicio
    ) {
        BigDecimal price = paidAmount != null ? paidAmount : productPrice;
        if (price == null || productDays == null || productDays <= 0 || endAt == null) return ZERO;

        // 👇 lógica adicional: si la compra fue hoy y la consulta es hoy
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = startAt != null ? startAt.atZone(ZoneOffset.UTC).toLocalDate() : null;
        if (startDate != null && startDate.equals(today)) {
            // devolver el precio completo
            return price.setScale(2, RoundingMode.HALF_UP);
        }

        long secondsRemaining = ChronoUnit.SECONDS.between(Instant.now(), endAt);
        if (secondsRemaining <= 0) return ZERO;

        BigDecimal daysRemaining = BigDecimal.valueOf(secondsRemaining)
                .divide(BigDecimal.valueOf(SECONDS_PER_DAY), 8, RoundingMode.HALF_UP);

        BigDecimal refund = price.multiply(daysRemaining)
                .divide(BigDecimal.valueOf(productDays), 8, RoundingMode.HALF_UP);

        if (feePercent != null && feePercent.compareTo(BigDecimal.ZERO) > 0) {
            refund = refund.multiply(BigDecimal.ONE.subtract(feePercent));
        }

        return refund.setScale(2, RoundingMode.HALF_UP);
    }


    private Integer computeDaysBetween(final Instant from, final Instant to, final boolean ceilPositive) {
        if (from == null || to == null) return null;
        long seconds = ChronoUnit.SECONDS.between(from, to);
        if (seconds == 0) return 0;
        double days = (double) seconds / SECONDS_PER_DAY;
        if (ceilPositive) {
            long ceil = (long) Math.ceil(days);
            return Math.toIntExact(ceil);
        } else {
            long floor = (long) Math.floor(days);
            return Math.toIntExact(floor);
        }
    }
    /**
     * Mapea una fila Object[] (resultado del SELECT nativo) a StockResponse.
     * El orden de índices debe coincidir exactamente con el SELECT del repository.
     *
     * SELECT order esperado (ejemplo usado anteriormente):
     * 0:id,
     * 1:product_id,
     * 2:product_name,
     * 3:provider_id,
     * 4:provider_name,
     * 5:provider_phone,
     * 6:stock_username,
     * 7:stock_password,
     * 8:stock_url,
     * 9:tipo,
     * 10:numero_perfil,
     * 11:pin,
     * 12:status,
     * 13:sold_at,
     * 14:start_at,
     * 15:end_at,
     * 16:client_name,
     * 17:client_phone
     */
    public StockResponse mapRowToStockResponse(Object[] row) {
        StockResponse r = new StockResponse();

        // id
        if (row[0] != null) {
            r.setId(((Number) row[0]).longValue());
        }

        // productId
        if (row[1] != null) {
            try { r.setProductId(UUID.fromString(row[1].toString())); } catch (Exception ignored) {}
        }

        // productName
        r.setProductName(row[2] != null ? row[2].toString() : null);

        // providerId (no existe en StockResponse pero lo dejamos en product)
        // providerName / providerPhone
        r.setProviderName(row[4] != null ? row[4].toString() : null);
        r.setProviderPhone(row[5] != null ? row[5].toString() : null);

        // username / password / url
        r.setUsername(row[6] != null ? row[6].toString() : null);
        r.setPassword(row[7] != null ? row[7].toString() : null);
        r.setUrl(row[8] != null ? row[8].toString() : null);

        // tipo -> TypeEnum (tu enum). Intentamos mapear por nombre.
        if (row[9] != null) {
            try {
                String tipoStr = row[9].toString();
                // Ajusta si tu TypeEnum tiene nombres distintos
                r.setType(Enum.valueOf(com.example.lunastreaming.model.TypeEnum.class, tipoStr));
            } catch (Exception ignored) {
                r.setType(null);
            }
        }

        // numeroPerfil -> numberProfile
        if (row[10] != null) {
            try { r.setNumberProfile(((Number) row[10]).intValue()); } catch (Exception ignored) { r.setNumberProfile(null); }
        }

        // pin
        r.setPin(row[11] != null ? row[11].toString() : null);

        // status
        r.setStatus(row[12] != null ? row[12].toString() : null);

        // soldAt
        if (row[13] instanceof Timestamp) {
            r.setSoldAt(((Timestamp) row[13]).toInstant());
        } else if (row[13] instanceof Instant) {
            r.setSoldAt((Instant) row[13]);
        } else if (row[13] != null) {
            try { r.setSoldAt(Timestamp.valueOf(row[13].toString()).toInstant()); } catch (Exception ignored) {}
        }

        // startAt
        if (row[14] instanceof Timestamp) {
            r.setStartAt(((Timestamp) row[14]).toInstant());
        } else if (row[14] instanceof Instant) {
            r.setStartAt((Instant) row[14]);
        }

        // endAt
        if (row[15] instanceof Timestamp) {
            r.setEndAt(((Timestamp) row[15]).toInstant());
        } else if (row[15] instanceof Instant) {
            r.setEndAt((Instant) row[15]);
        }

        // clientName / clientPhone
        r.setClientName(row[16] != null ? row[16].toString() : null);
        r.setClientPhone(row[17] != null ? row[17].toString() : null);

        // published / refund / daysRemaining / buyer fields are not selected in the native query above.
        // Si los necesitas, añade las columnas al SELECT y mapea aquí:
        // r.setPublished(...); r.setRefund(...); r.setDaysRemaining(...); r.setBuyerId(...); r.setBuyerUsername(...);

        return r;
    }



}
