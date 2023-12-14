package io.kalix.api;

import com.google.api.HttpBody;
import com.typesafe.config.Config;
import io.kalix.application.CallForPaperEntity;
import io.kalix.application.CreateCallForPaper;
import io.kalix.application.SlackClient;
import io.kalix.application.SlackResponse;
import io.kalix.view.AllCallForPaperView;
import io.kalix.view.CallForPaperList;
import io.kalix.view.CallForPaperView;
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
  public Effect<HttpBody> postList(@RequestParam String token) {

    if (notValid(token)) {
      return effects().error("Access denied", StatusCode.ErrorCode.FORBIDDEN);
    }

    CompletionStage<CallForPaperList> cfps = componentClient
      .forView()
      .call(AllCallForPaperView::getCallForPapers)
      .execute();

    return effects().asyncReply(cfps.thenApply(callForPaperList ->
      slackClient.getCfpsListPayload(callForPaperList.callForPaperViews())));
  }

  private boolean notValid(String token) {
    return !config.getString("cfp.verification-token").equals(token);
  }

  @PostMapping("/delete")
  public Effect<HttpBody> openDeleteView(@RequestParam String token, @RequestParam("trigger_id") String triggerId) {

    if (notValid(token)) {
      return effects().error("Access denied", StatusCode.ErrorCode.FORBIDDEN);
    }

    CompletionStage<CallForPaperList> cfps = componentClient
      .forView()
      .call(AllCallForPaperView::getCallForPapers)
      .execute();

    CompletionStage<Effect<HttpBody>> openDeleteView = cfps.thenCompose(callForPaperList ->
        slackClient.openCfpsToDelete(callForPaperList.callForPaperViews(), triggerId, DELETE_CFP_CALLBACK_ID, DELETE_CFP_ID_FIELD))
      .thenApply(res -> switch (res) {
        case SlackResponse.Success ignore -> effects().reply(HttpBody.newBuilder().build());
        case SlackResponse.Failure failure -> {
          logger.error("open cfp to delete failed, status: {}, msg: {}, exception: {}", failure.code(), failure.message(), failure.exception());
          yield effects().error("Failed to open cfp to delete");
        }
      });
    return effects().asyncEffect(openDeleteView);
  }

  @PostMapping("/add")
  public Effect<HttpBody> openAddView(@RequestParam String token, @RequestParam("trigger_id") String triggerId) {
    if (notValid(token)) {
      return effects().error("Access denied", StatusCode.ErrorCode.FORBIDDEN);
    }
    CompletionStage<Effect<HttpBody>> openAddView = slackClient.openAddCfp(triggerId, ADD_CFP_CALLBACK_ID, CONFERENCE_NAME_FIELD, CONFERENCE_LINK_FIELD, CONFERENCE_CFP_DEADLINE_FIELD)
      .thenApply(res -> switch (res) {
        case SlackResponse.Success ignore -> effects().reply(HttpBody.newBuilder().build());
        case SlackResponse.Failure failure -> {
          logger.error("open add cfp failed, status: {}, msg: {}, exception: {}", failure.code(), failure.message(), failure.exception());
          yield effects().error("Failed to open add cfp");
        }
      });
    return effects().asyncEffect(openAddView);
  }

  @PostMapping("/submit")
  public Effect<HttpBody> submit(@RequestParam String payload) {

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
        return effects().reply(HttpBody.newBuilder().build());
      }
    } else {
      logger.debug("Ignoring view submission");
      return effects().reply(HttpBody.getDefaultInstance());
    }
  }

  private Effect<HttpBody> handleDelete(ViewSubmission viewSubmission) {
    String cfpId = viewSubmission.view().state().getValues().get(DELETE_CFP_ID_FIELD).get(DELETE_CFP_ID_FIELD).getSelectedOption().getValue();
    CompletionStage<Effect<HttpBody>> deleteCfp = componentClient.forValueEntity(cfpId).call(CallForPaperEntity::delete).execute()
      .handle((s, throwable) -> {
        if (throwable != null) {
          logger.error("Failed to delete cfp: " + cfpId, throwable);
          return effects().error("Failed to delete cfp: " + cfpId);
        } else {
          logger.info("Deleted cfp: " + cfpId);
          return effects().reply(HttpBody.newBuilder().build());
        }
      });
    return effects().asyncEffect(deleteCfp);
  }

  private Effect<HttpBody> handleAdd(ViewSubmission viewSubmission) {
    String conferenceName = viewSubmission.view().state().getValues().get(CONFERENCE_NAME_FIELD).get(CONFERENCE_NAME_FIELD).getValue();
    String conferenceLink = viewSubmission.view().state().getValues().get(CONFERENCE_LINK_FIELD).get(CONFERENCE_LINK_FIELD).getValue();
    String conferenceCfpDeadline = viewSubmission.view().state().getValues().get(CONFERENCE_CFP_DEADLINE_FIELD).get(CONFERENCE_CFP_DEADLINE_FIELD).getSelectedDate();
    var cfpId = UUID.randomUUID().toString();
    CreateCallForPaper callForPaper = new CreateCallForPaper(conferenceName, LocalDate.parse(conferenceCfpDeadline), conferenceLink, viewSubmission.user().username());
    CompletionStage<Effect<HttpBody>> addCfp = componentClient.forValueEntity(cfpId)
      .call(CallForPaperEntity::create)
      .params(callForPaper)
      .execute()
      .thenCompose(cfp -> slackClient.postNewCfp(CallForPaperView.of(cfp)))
      .handle((result, throwable) -> {
        if (throwable != null) {
          logger.error("Failed to add cfp: " + callForPaper, throwable);
          return effects().error("Failed to add cfp: " + callForPaper);
        } else {
          logger.info("Added cfp: " + callForPaper);
          return effects().reply(HttpBody.newBuilder().build());
        }
      });
    return effects().asyncEffect(addCfp);
  }
}
