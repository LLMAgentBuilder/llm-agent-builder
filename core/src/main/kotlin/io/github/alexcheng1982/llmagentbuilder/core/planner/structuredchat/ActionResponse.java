package io.github.alexcheng1982.llmagentbuilder.core.planner.structuredchat;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ActionResponse {

  @JsonProperty("action")
  String action;
  @JsonProperty("action_input")
  String actionInput;
}