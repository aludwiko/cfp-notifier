package io.kalix.api;

import com.typesafe.config.Config;
import io.kalix.application.CallForPaperEntity;
import io.kalix.application.CreateCallForPaper;
import io.kalix.application.SlackClient;
import io.kalix.application.SlackResponse;
import io.kalix.view.AllCallForPaperView;
import io.kalix.view.CallForPaperList;
import io.kalix.view.CallForPaperView;
import kalix.javasdk.HttpResponse;
import kalix.javasdk.StatusCode;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static kalix.javasdk.StatusCode.Success.OK;

@RequestMapping("/api/cfp")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class CallForPaperController extends Action {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final ComponentClient componentClient;
  private final SlackClient slackClient;
  private final Config config;
  final static String DELETE_CFP_CALLBACK_ID = "delete_cfp";
  final static String DELETE_CFP_ID_FIELD = "cfp_id";
  final static String ADD_CFP_CALLBACK_ID = "add_cfp";
  final static String CONFERENCE_NAME_FIELD = "conference_name";
  final static String CONFERENCE_LINK_FIELD = "conference_link";
  final static String CONFERENCE_CFP_DEADLINE_FIELD = "conference_cfp_deadline";


  public CallForPaperController(ComponentClient componentClient, SlackClient slackClient, Config config) {
    this.componentClient = componentClient;
    this.slackClient = slackClient;
    this.config = config;
  }

  @PostMapping("/list")
  public Effect<HttpResponse> postList(@RequestParam String token) {

    if (notValid(token)) {
      return effects().error("Access denied", StatusCode.ErrorCode.FORBIDDEN);
    }

    CompletionStage<CallForPaperList> cfps = componentClient
      .forView()
      .call(AllCallForPaperView::getCallForPapers)
      .execute();

    return effects().asyncReply(cfps.thenApply(callForPaperList -> {
        var payload = slackClient.getCfpsListPayload(callForPaperList.callForPaperViews());
        return HttpResponse.of(OK, "application/json", payload.getBytes());
      }
    ));
  }

  private boolean notValid(String token) {
    return !config.getString("cfp.notifier.verification-token").equals(token);
  }

  @PostMapping("/delete")
  public Effect<HttpResponse> openDeleteView(@RequestParam String token, @RequestParam("trigger_id") String triggerId) {

    if (notValid(token)) {
      return effects().error("Access denied", StatusCode.ErrorCode.FORBIDDEN);
    }

    CompletionStage<CallForPaperList> cfps = componentClient
      .forView()
      .call(AllCallForPaperView::getCallForPapers)
      .execute();

    CompletionStage<Effect<HttpResponse>> openDeleteView = cfps.thenCompose(callForPaperList ->
        slackClient.openCfpsToDelete(callForPaperList.callForPaperViews(), triggerId, DELETE_CFP_CALLBACK_ID, DELETE_CFP_ID_FIELD))
      .thenApply(res -> switch (res) {
        case SlackResponse.Response ignore -> effects().reply(HttpResponse.ok());
        case SlackResponse.Failure failure -> {
          logger.error("open cfp to delete failed, status: {}, msg: {}, exception: {}", failure.code(), failure.message(), failure.exception());
          yield effects().error("Failed to open cfp to delete");
        }
      });
    return effects().asyncEffect(openDeleteView);
  }

  @PostMapping("/add")
  public Effect<HttpResponse> openAddView(@RequestParam String token, @RequestParam("trigger_id") String triggerId) {
    if (notValid(token)) {
      logger.debug("Processing cfp add request rejected");
      return effects().error("Access denied", StatusCode.ErrorCode.FORBIDDEN);
    }
    logger.debug("Processing cfp add request, opening add dialog");
    CompletionStage<Effect<HttpResponse>> openAddView = slackClient.openAddCfp(triggerId, ADD_CFP_CALLBACK_ID, CONFERENCE_NAME_FIELD, CONFERENCE_LINK_FIELD, CONFERENCE_CFP_DEADLINE_FIELD)
      .thenApply(res -> switch (res) {
        case SlackResponse.Response ignore -> effects().reply(HttpResponse.ok());
        case SlackResponse.Failure failure -> {
          logger.error("open add cfp failed, status: {}, msg: {}, exception: {}", failure.code(), failure.message(), failure.exception());
          yield effects().error("Failed to open add cfp");
        }
      });
    return effects().asyncEffect(openAddView);
  }

  @PostMapping("/submit")
  public Effect<HttpResponse> submit(@RequestParam String payload) {

    ViewSubmission viewSubmission = ViewSubmissionParser.parse(payload);

    logger.trace("Payload: {}", payload);
    logger.trace("View submission: {}", viewSubmission);

    if (notValid(viewSubmission.token())) {
      return effects().error("Access denied", StatusCode.ErrorCode.FORBIDDEN);
    }

    if (viewSubmission.type().equals("view_submission")) {
      if (viewSubmission.view().callbackId().equals(DELETE_CFP_CALLBACK_ID)) {
        return handleDelete(viewSubmission);
      } else if (viewSubmission.view().callbackId().equals(ADD_CFP_CALLBACK_ID)) {
        return handleAdd(viewSubmission);
      } else {
        logger.info("Unknown callbackId {}", viewSubmission.view().callbackId());
        return effects().reply(HttpResponse.ok());
      }
    } else {
      logger.debug("Ignoring view submission");
      return effects().reply(HttpResponse.ok());
    }
  }

  private Effect<HttpResponse> handleDelete(ViewSubmission viewSubmission) {
    String cfpId = viewSubmission.view().state().getValues().get(DELETE_CFP_ID_FIELD).get(DELETE_CFP_ID_FIELD).getSelectedOption().getValue();
    CompletionStage<Effect<HttpResponse>> deleteCfp = componentClient.forValueEntity(cfpId).call(CallForPaperEntity::delete).execute()
      .handle((s, throwable) -> {
        if (throwable != null) {
          logger.error("Failed to delete cfp: " + cfpId, throwable);
          return effects().error("Failed to delete cfp: " + cfpId);
        } else {
          logger.info("Deleted cfp: " + cfpId);
          return effects().reply(HttpResponse.ok());
        }
      });
    return effects().asyncEffect(deleteCfp);
  }

  private Effect<HttpResponse> handleAdd(ViewSubmission viewSubmission) {
    String conferenceName = viewSubmission.view().state().getValues().get(CONFERENCE_NAME_FIELD).get(CONFERENCE_NAME_FIELD).getValue();
    String conferenceLink = viewSubmission.view().state().getValues().get(CONFERENCE_LINK_FIELD).get(CONFERENCE_LINK_FIELD).getValue();
    String conferenceCfpDeadline = viewSubmission.view().state().getValues().get(CONFERENCE_CFP_DEADLINE_FIELD).get(CONFERENCE_CFP_DEADLINE_FIELD).getSelectedDate();
    var cfpId = UUID.randomUUID().toString();
    CreateCallForPaper callForPaper = new CreateCallForPaper(conferenceName, LocalDate.parse(conferenceCfpDeadline), conferenceLink, viewSubmission.user().username());
    CompletionStage<Effect<HttpResponse>> addCfp = componentClient.forValueEntity(cfpId)
      .call(CallForPaperEntity::create)
      .params(callForPaper)
      .execute()
      .thenCompose(cfp -> slackClient.postNewCfp(CallForPaperView.of(cfp)))
      .handle((result, throwable) -> {
        if (throwable != null) {
          logger.error("Failed to add cfp: " + callForPaper, throwable);
          return effects().error("Failed to add cfp: " + callForPaper);
        } else {
          return switch (result) {
            case SlackResponse.Response response -> {
              if (response.code() != 200) {
                logger.error("Failed to post new cfp {}, status: {}, msg: {}", callForPaper, response.code(), response.message());
              } else {
                logger.info("Added cfp: " + callForPaper);
              }
              yield effects().reply(HttpResponse.ok());
            }
            case SlackResponse.Failure failure -> {
              logger.error("Failed to post new cfp: " + callForPaper, failure.exception());
              yield effects().reply(HttpResponse.ok());
            }
          };
        }
      });
    return effects().asyncEffect(addCfp);
  }
}
