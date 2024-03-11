package io.kalix.api;

import io.kalix.application.CallForPaperEntity;
import io.kalix.application.Notify;
import io.kalix.domain.CallForPaperReminder;
import kalix.javasdk.HttpResponse;
import kalix.javasdk.action.Action;
import kalix.javasdk.client.ComponentClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/cfp/backoffice")
public class CallForPaperBackofficeController extends Action {

  private final ComponentClient componentClient;

  public CallForPaperBackofficeController(ComponentClient componentClient) {this.componentClient = componentClient;}


  @PostMapping("/trigger-notification/{cfpId}")
  public Action.Effect<HttpResponse> triggerNotification(@PathVariable String cfpId) {

    return effects().asyncReply(
      componentClient
        .forValueEntity(cfpId)
        .call(CallForPaperEntity::get).execute()
        .thenApply(callForPaper ->
          componentClient
            .forAction()
            .call(Notify::runNotification)
            .params(CallForPaperReminder.of(callForPaper, 0)).execute())
        .thenApply(__ -> HttpResponse.ok()));
  }
}
