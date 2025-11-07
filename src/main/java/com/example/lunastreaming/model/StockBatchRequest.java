package com.example.lunastreaming.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
public class StockBatchRequest {

    private List<StockResponse> stocks;
}
