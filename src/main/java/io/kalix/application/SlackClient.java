package io.kalix.application;

import com.google.api.HttpBody;
import io.kalix.view.CallForPaperView;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface SlackClient {

  HttpBody getCfpsListPayload(List<CallForPaperView> openCallForPapers);

  CompletionStage<SlackResponse> postNewCfp(CallForPaperView callForPaperView);

  CompletionStage<SlackResponse> openCfpsToDelete(List<CallForPaperView> openCallForPapers, String triggerId, String callbackId, String cfpIdField);

  CompletionStage<SlackResponse> openAddCfp(String triggerId, String callbackId, String conferenceNameField, String conferenceLinkField, String conferenceCfpDeadlineField);
}
