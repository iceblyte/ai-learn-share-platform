package com.learning.platform.dto;

import com.learning.platform.entity.Resource;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class NlSearchResult {

    private Map<String, Object> parsedIntent;
    private List<Resource> results;
    private long total;

    public NlSearchResult(Map<String, Object> parsedIntent, List<Resource> results, long total) {
        this.parsedIntent = parsedIntent;
        this.results = results;
        this.total = total;
    }
}
