package com.company.smartsupport.common;

import java.util.List;

public record PageResult<T>(List<T> items, int pageNo, int pageSize, long total, boolean hasNext) {

    public static <T> PageResult<T> of(List<T> allItems, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, allItems.size());
        int toIndex = Math.min(fromIndex + safePageSize, allItems.size());
        List<T> pageItems = allItems.subList(fromIndex, toIndex);
        return new PageResult<>(pageItems, safePageNo, safePageSize, allItems.size(), toIndex < allItems.size());
    }
}
