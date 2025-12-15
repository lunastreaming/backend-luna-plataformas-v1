package com.example.lunastreaming.builder;

import com.example.lunastreaming.model.ProductDto;
import com.example.lunastreaming.model.ProductEntity;
import com.example.lunastreaming.model.UserEntity;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductBuilder {

    public ProductDto productDtoFromEntity(ProductEntity productEntity,
                                           String categoryName, String providerName, UserEntity provider) {
        if (productEntity == null) return null;

        String providerStatus = null;
        if (provider != null && provider.getProviderProfile() != null) {
            providerStatus = provider.getProviderProfile().getStatus();
        }

        return ProductDto.builder()
                .id(productEntity.getId())
                .providerId(productEntity.getProviderId())
                .providerName(providerName)
                .providerStatus(providerStatus)
                .categoryId(productEntity.getCategoryId())
                .categoryName(categoryName)
                .name(productEntity.getName())
                .terms(productEntity.getTerms())
                .productDetail(productEntity.getProductDetail())
                .requestDetail(productEntity.getRequestDetail())
                .days(productEntity.getDays())
                .salePrice(productEntity.getSalePrice())
                .renewalPrice(productEntity.getRenewalPrice())
                .isRenewable(productEntity.getIsRenewable())
                .isOnRequest(productEntity.getIsOnRequest())
                .active(productEntity.getActive())
                .createdAt(productEntity.getCreatedAt())
                .updatedAt(productEntity.getUpdatedAt())
                .imageUrl(productEntity.getImageUrl())
                .publishStart(productEntity.getPublishStart())
                .publishEnd(productEntity.getPublishEnd())
                .daysRemaining(productEntity.getDaysRemaining())
                .build();
    }


}
