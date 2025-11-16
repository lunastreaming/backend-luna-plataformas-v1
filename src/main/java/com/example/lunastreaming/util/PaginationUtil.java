package com.example.lunastreaming.util;

import com.example.lunastreaming.model.PagedResponse;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;

/**
 * Helpers para paginación: conversión de Spring Page<T> a PagedResponse<T>.
 */
public final class PaginationUtil {

    private PaginationUtil() { /* util class - no instances */ }

    public static <T> PagedResponse<T> toPagedResponse(Page<T> page) {
        if (page == null) {
            return PagedResponse.<T>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(0)
                    .totalElements(0L)
                    .totalPages(0)
                    .build();
        }

        return PagedResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

}