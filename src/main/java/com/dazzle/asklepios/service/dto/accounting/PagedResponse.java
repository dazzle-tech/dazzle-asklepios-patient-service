package com.dazzle.asklepios.service.dto.accounting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.Serializable;
import java.util.List;

public record PagedResponse<T>(

        List<T> content,

        int number,

        int size,

        long totalElements,

        int totalPages,

        boolean first,

        boolean last

) implements Serializable {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    public static <T> PagedResponse<T> from(List<T> items, Pageable pageable) {
        if (items == null || items.isEmpty()) {
            return new PagedResponse<>(
                    List.of(),
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    0,
                    0,
                    true,
                    true
            );
        }

        int pageNumber = Math.max(pageable.getPageNumber(), 0);
        int pageSize = Math.max(pageable.getPageSize(), 1);
        int fromIndex = Math.min(pageNumber * pageSize, items.size());
        int toIndex = Math.min(fromIndex + pageSize, items.size());
        int totalPages = (int) Math.ceil(items.size() / (double) pageSize);

        return new PagedResponse<>(
                items.subList(fromIndex, toIndex),
                pageNumber,
                pageSize,
                items.size(),
                totalPages,
                pageNumber <= 0,
                pageNumber >= Math.max(totalPages - 1, 0)
        );
    }
}
