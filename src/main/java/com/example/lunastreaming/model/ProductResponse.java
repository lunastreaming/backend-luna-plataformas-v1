package com.example.lunastreaming.model;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ProductResponse {

    private ProductEntity product;

    private List<StockResponse> stockResponses;

}
