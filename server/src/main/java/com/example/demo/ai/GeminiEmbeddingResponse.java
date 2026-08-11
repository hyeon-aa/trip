package com.example.demo.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiEmbeddingResponse {
  public Embedding embedding;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Embedding {
    public List<Double> values;
  }
}
