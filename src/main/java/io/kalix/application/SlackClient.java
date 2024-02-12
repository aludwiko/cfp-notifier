package io.kalix.application;

import io.kalix.domain.CallForPaperReminder;
import io.kalix.view.CallForPaperView;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface SlackClient {

  String getCfpsListPayload(List<CallForPaperView> openCallForPapers);

  CompletionStage<SlackResponse> postNewCfp(CallForPaperView callForPaperView);

  CompletionStage<SlackResponse> openCfpsToDelete(List<CallForPaperView> openCallForPapers, String triggerId, String callbackId, String cfpIdField);

  CompletionStage<SlackResponse> openAddCfp(String triggerId, String callbackId, String conferenceNameField, String conferenceLinkField, String conferenceCfpDeadlineField);

  CompletionStage<SlackResponse> notifyAboutOpenCfp(CallForPaperReminder callForPaperReminder);
}
