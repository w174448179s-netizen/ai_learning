package com.example.aigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamChunk {
    private String kind;
    private String text;
    private Boolean done;
    private Integer totalTokens;
}