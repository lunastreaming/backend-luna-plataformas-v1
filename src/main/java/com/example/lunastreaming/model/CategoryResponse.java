package com.example.lunastreaming.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryResponse {

    private Long id;
    private String name;
    private String imageUrl;
    private String status;
    private String description;

}
