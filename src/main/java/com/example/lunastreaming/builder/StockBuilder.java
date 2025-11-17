package com.example.lunastreaming.builder;

import com.example.lunastreaming.model.ProductEntity;
import com.example.lunastreaming.model.StockEntity;
import com.example.lunastreaming.model.StockResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static java.math.BigDecimal.ZERO;


@Component
public class StockBuilder {

    public StockResponse toStockResponse(StockEntity stockEntity) {
        if (stockEntity == null) return null;

        UUID productId = null;
        String productName = null;
        if (stockEntity.getProduct() != null) {
            productId = stockEntity.getProduct().getId();
            productName = stockEntity.getProduct().getName();
        }

        UUID buyerId = null;
        String buyerUsername = null;
        if (stockEntity.getBuyer() != null) {
            buyerId = stockEntity.getBuyer().getId();
            buyerUsername = stockEntity.getBuyer().getUsername();
        }

        Instant soldAtInstant = null;
        if (stockEntity.getSoldAt() != null) {
            soldAtInstant = stockEntity.getSoldAt().toInstant();
        }

        BigDecimal refund = ZERO;

        if (stockEntity.getEndAt() != null) {
            BigDecimal productPrice = stockEntity.getProduct() != null ? stockEntity.getProduct().getSalePrice() : null;
            Integer productDays = stockEntity.getProduct() != null ? stockEntity.getProduct().getDays() : null;
            refund = computeRefund(productPrice, productPrice, productDays, stockEntity.getEndAt(), BigDecimal.ZERO);
        }

        // calcular daysRemaining y daysPublished
        Integer daysRemaining = null;
        if (stockEntity.getEndAt() != null) {
            daysRemaining = computeDaysBetween(Instant.now(), stockEntity.getEndAt(), true);
            if (daysRemaining < 0) daysRemaining = 0;
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
                .clientName(stockEntity.getClientName())
                .clientPhone(stockEntity.getClientPhone())
                .startAt(stockEntity.getStartAt())
                .endAt(stockEntity.getEndAt())
                .daysRemaining(daysRemaining)
                .refund(refund)
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

    public BigDecimal computeRefund(final BigDecimal paidAmount, final BigDecimal productPrice, final Integer productDays, final Instant endAt, final BigDecimal feePercent) {
        BigDecimal price = paidAmount != null ? paidAmount : productPrice;
        if (price == null || productDays == null || productDays <= 0 || endAt == null) return ZERO;

        long secondsRemaining = ChronoUnit.SECONDS.between(Instant.now(), endAt);
        if (secondsRemaining <= 0) return ZERO;

        BigDecimal daysRemaining = BigDecimal.valueOf(secondsRemaining).divide(BigDecimal.valueOf(SECONDS_PER_DAY), 8, RoundingMode.HALF_UP);
        BigDecimal refund = price.multiply(daysRemaining).divide(BigDecimal.valueOf(productDays), 8, RoundingMode.HALF_UP);
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
            return Math.toIntExact(Math.max(0, ceil));
        } else {
            long floor = (long) Math.floor(days);
            return Math.toIntExact(Math.max(0, floor));
        }
    }


}
