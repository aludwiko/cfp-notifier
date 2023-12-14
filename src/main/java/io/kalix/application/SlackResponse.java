package io.kalix.application;

public sealed interface SlackResponse {
  record Success(int code, String message) implements SlackResponse {
  }

  record Failure(int code, String message, Throwable exception) implements SlackResponse {
  }
}
