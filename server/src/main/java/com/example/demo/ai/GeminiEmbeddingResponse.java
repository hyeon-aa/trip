package com.example.demo.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiEmbeddingResponse {
    public Embedding embedding;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Embedding {
        public List<Double> values;
    }
}