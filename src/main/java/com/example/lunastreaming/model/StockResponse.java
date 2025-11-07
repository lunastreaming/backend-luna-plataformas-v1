package com.example.lunastreaming.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.UUID;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StockResponse {

    private Long id;
    private UUID productId;
    private String productName;
    private String username;
    private String password;
    private String url;
    @JsonProperty("tipo")
    private TypeEnum type;
    @JsonProperty("numeroPerfil")
    private Integer numberProfile;
    private String status;
    private ProductEntity product;
    private String pin;

}
