package com.example.superheroproxy.dto;

import java.util.List;

public class PaginatedSearchResultDto {
    private List<SearchResultDto> results;
    private int currentPage;
    private int totalPages;
    private long totalCount;

    public PaginatedSearchResultDto(List<SearchResultDto> results, int currentPage, int totalPages, long totalCount) {
        this.results = results;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.totalCount = totalCount;
    }

    public List<SearchResultDto> getResults() {
        return results;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public long getTotalCount() {
        return totalCount;
    }
} 