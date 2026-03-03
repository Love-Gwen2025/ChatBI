package com.chatbi.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public record PageResponse<T>(
        List<T> data,
        long total,
        int page,
        int size,
        int totalPages
) {
    public static <T> PageResponse<T> of(IPage<T> iPage) {
        return new PageResponse<>(
                iPage.getRecords(),
                iPage.getTotal(),
                (int) iPage.getCurrent(),
                (int) iPage.getSize(),
                (int) iPage.getPages()
        );
    }

    public static <T> PageResponse<T> of(List<T> data, long total, int page, int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new PageResponse<>(data, total, page, size, totalPages);
    }
}
