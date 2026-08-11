package com.example.demo.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GroqResponse {
  public List<Choice> choices;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Choice {
    public Message message;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Message {
    public String content;
  }
}
