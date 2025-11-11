package com.example.lunastreaming.builder;

import com.example.lunastreaming.model.StockEntity;
import com.example.lunastreaming.model.StockResponse;
import org.springframework.stereotype.Component;


@Component
public class StockBuilder {

    public StockResponse toStockResponse(StockEntity stockEntity) {

        return StockResponse
                .builder()
                .id(stockEntity.getId())
                .productId(stockEntity.getProduct().getId())
                .productName(stockEntity.getProduct().getName())
                .username(stockEntity.getUsername())
                .password(stockEntity.getPassword())
                .url(stockEntity.getUrl())
                .type(stockEntity.getTipo())
                .numberProfile(stockEntity.getNumeroPerfil())
                .status(stockEntity.getStatus())
                .pin(stockEntity.getPin())
                .build();
    }

    public StockEntity fromStockResponse(StockResponse stockResponse) {
        return StockEntity
                .builder()
                .username(stockResponse.getUsername())
                .password(stockResponse.getPassword())
                .url(stockResponse.getUrl())
                .tipo(stockResponse.getType())
                .numeroPerfil(stockResponse.getNumberProfile())
                .status("inactive")
                .pin(stockResponse.getPin())
                .build();
    }

}
