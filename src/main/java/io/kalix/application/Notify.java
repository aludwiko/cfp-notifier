package io.kalix.application;

import akka.Done;
import io.kalix.domain.CallForPaperReminder;
import kalix.javasdk.action.Action;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;


@RequestMapping("/notify")
public class Notify extends Action {

  private final SlackClient slackClient;

  public Notify(SlackClient slackClient) {
    this.slackClient = slackClient;
  }

  @PostMapping
  public Effect<Done> runNotification(@RequestBody CallForPaperReminder callForPaperReminder) {
    return effects().asyncReply(slackClient.notifyAboutOpenCfp(callForPaperReminder).thenApply(__ -> Done.getInstance()));
  }
}
