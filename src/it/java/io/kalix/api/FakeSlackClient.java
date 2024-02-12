package io.kalix.api;

import com.typesafe.config.ConfigFactory;
import io.kalix.application.SlackClient;
import io.kalix.application.SlackResponse;
import io.kalix.domain.CallForPaperReminder;
import io.kalix.infrastructure.BlockingSlackClient;
import io.kalix.view.CallForPaperView;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@Profile("it-test")
public class FakeSlackClient implements SlackClient {

  private SlackClient slackClient = new BlockingSlackClient(ConfigFactory.defaultApplication());

  @Override
  public String getCfpsListPayload(List<CallForPaperView> openCallForPapers) {
    return slackClient.getCfpsListPayload(openCallForPapers);
  }

  @Override
  public CompletionStage<SlackResponse> postNewCfp(CallForPaperView callForPaperView) {
    return CompletableFuture.completedFuture(new SlackResponse.Response(200, "ok"));
  }

  @Override
  public CompletionStage<SlackResponse> openCfpsToDelete(List<CallForPaperView> openCallForPapers, String triggerId, String callbackId, String cfpIdField) {
    return CompletableFuture.completedFuture(new SlackResponse.Response(200, "ok"));
  }

  @Override
  public CompletionStage<SlackResponse> openAddCfp(String triggerId, String callbackId, String conferenceNameField, String conferenceLinkField, String conferenceCfpDeadlineField) {
    return CompletableFuture.completedFuture(new SlackResponse.Response(200, "ok"));
  }

  @Override
  public CompletionStage<SlackResponse> notifyAboutOpenCfp(CallForPaperReminder callForPaperReminder) {
    return CompletableFuture.completedFuture(new SlackResponse.Response(200, "ok"));
  }
}
