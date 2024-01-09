package io.kalix.api;

import com.google.gson.Gson;
import com.google.protobuf.any.Any;
import com.slack.api.SlackConfig;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.RichTextBlock;
import com.slack.api.model.block.element.BlockElement;
import com.slack.api.model.block.element.RichTextListElement;
import com.slack.api.model.block.element.RichTextSectionElement;
import com.slack.api.model.view.ViewState;
import com.slack.api.util.json.GsonFactory;
import com.slack.api.webhook.Payload;
import io.kalix.Main;
import io.kalix.application.CallForPaperEntity;
import io.kalix.application.CreateCallForPaper;
import io.kalix.view.AllCallForPaperView;
import io.kalix.view.CallForPaperList;
import io.kalix.view.CallForPaperView;
import kalix.javasdk.DeferredCall;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.kalix.api.CallForPaperController.ADD_CFP_CALLBACK_ID;
import static io.kalix.api.CallForPaperController.CONFERENCE_CFP_DEADLINE_FIELD;
import static io.kalix.api.CallForPaperController.CONFERENCE_LINK_FIELD;
import static io.kalix.api.CallForPaperController.CONFERENCE_NAME_FIELD;
import static io.kalix.api.CallForPaperController.DELETE_CFP_CALLBACK_ID;
import static io.kalix.api.CallForPaperController.DELETE_CFP_ID_FIELD;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = Main.class)
@ActiveProfiles("it-test")
class CallForPaperControllerIntegrationTest extends KalixIntegrationTestKitSupport {

  private final Gson gson = GsonFactory.createSnakeCase(SlackConfig.DEFAULT);

  @Autowired
  private WebClient webClient;
  private String token = "123";

  @Test
  public void shouldOpenAddView() throws ExecutionException, InterruptedException, TimeoutException {
    //given
    String triggerId = "trigger123";

    //when
    ResponseEntity<String> response =
      webClient
        .post()
        .uri("/api/cfp/add")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .bodyValue("token=" + token + "&trigger_id=" + triggerId)
        .retrieve()
        .toEntity(String.class)
        .block(timeout);

    //then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNull();
  }

  @Test
  public void shouldOpenDeleteView() throws ExecutionException, InterruptedException, TimeoutException {
    //given
    String triggerId = "trigger123";

    //when
    ResponseEntity<String> response =
      webClient
        .post()
        .uri("/api/cfp/delete")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .bodyValue("token=" + token + "&trigger_id=" + triggerId)
        .retrieve()
        .toEntity(String.class)
        .block(timeout);

    //then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNull();
  }

  @Test
  public void shouldAddCfp() throws ExecutionException, InterruptedException, TimeoutException, UnsupportedEncodingException {
    //given
    String conferenceName = "My conference";
    String conferenceLink = "url";
    String cfpDeadline = "2024-01-01";
    ViewState.Value conferenceNameValue = new ViewState.Value();
    conferenceNameValue.setValue(conferenceName);
    ViewState.Value conferenceLinkValue = new ViewState.Value();
    conferenceLinkValue.setValue(conferenceLink);
    ViewState.Value cfpDeadlinekValue = new ViewState.Value();
    cfpDeadlinekValue.setSelectedDate(cfpDeadline);
    Map<String, Map<String, ViewState.Value>> values = Map.of(CONFERENCE_NAME_FIELD, Map.of(CONFERENCE_NAME_FIELD, conferenceNameValue),
      CONFERENCE_LINK_FIELD, Map.of(CONFERENCE_LINK_FIELD, conferenceLinkValue),
      CONFERENCE_CFP_DEADLINE_FIELD, Map.of(CONFERENCE_CFP_DEADLINE_FIELD, cfpDeadlinekValue));
    View view = new View(ADD_CFP_CALLBACK_ID, ViewState.builder().values(values).build());
    ViewSubmission viewSubmission = new ViewSubmission("view_submission", token, new ViewSubmissionUser("andrzej"), view);

    //when
    ResponseEntity<String> response =
      webClient
        .post()
        .uri("/api/cfp/submit")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .bodyValue("payload=" + URLEncoder.encode(gson.toJson(viewSubmission), UTF_8))
        .retrieve()
        .toEntity(String.class)
        .block(timeout);


    //then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    await()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        CallForPaperList callForPaperList = execute(componentClient.forView().call(AllCallForPaperView::getCallForPapers));
        assertThat(callForPaperList.callForPaperViews()).hasSize(1);
        CallForPaperView cfp = callForPaperList.callForPaperViews().getLast();
        assertThat(cfp.conferenceName()).isEqualTo(conferenceName);
        assertThat(cfp.conferenceLink()).isEqualTo(conferenceLink);
        assertThat(cfp.deadline()).isEqualTo(cfpDeadline);
      });
  }

  @Test
  public void shouldDeleteCfp() throws ExecutionException, InterruptedException, TimeoutException, UnsupportedEncodingException {
    //given
    String cfpId = UUID.randomUUID().toString();
    execute(componentClient.forValueEntity(cfpId)
      .call(CallForPaperEntity::create)
      .params(new CreateCallForPaper("My conference", LocalDate.now(), "url", "andrzej")));


    ViewState.SelectedOption selectedOption = new ViewState.SelectedOption();
    selectedOption.setValue(cfpId);
    ViewState.Value cfpIdValue = new ViewState.Value();
    cfpIdValue.setSelectedOption(selectedOption);
    Map<String, Map<String, ViewState.Value>> values = Map.of(DELETE_CFP_ID_FIELD, Map.of(DELETE_CFP_ID_FIELD, cfpIdValue));
    View view = new View(DELETE_CFP_CALLBACK_ID, ViewState.builder().values(values).build());
    ViewSubmission viewSubmission = new ViewSubmission("view_submission", token, new ViewSubmissionUser("andrzej"), view);

    //when
    ResponseEntity<String> response =
      webClient
        .post()
        .uri("/api/cfp/submit")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .bodyValue("payload=" + URLEncoder.encode(gson.toJson(viewSubmission), UTF_8))
        .retrieve()
        .toEntity(String.class)
        .block(timeout);


    //then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ResponseEntity<String> getCfpResponse =
      webClient
        .get()
        .uri("/cfp/" + cfpId)
        .retrieve()
        .toEntity(String.class)
        .onErrorResume(WebClientResponseException.class, error -> Mono.just(ResponseEntity.status(error.getStatusCode()).body(error.getResponseBodyAsString())))
        .block(timeout);
    assertThat(getCfpResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  public void shouldListCfps() throws InterruptedException {
    //given
    String cfpId1 = "1";
    CreateCallForPaper callForPaper1 = new CreateCallForPaper("My conference 1", LocalDate.parse("2021-01-03"), "url", "andrzej");
    execute(componentClient.forValueEntity(cfpId1).call(CallForPaperEntity::create).params(callForPaper1));

    String cfpId2 = "2";
    CreateCallForPaper callForPaper2 = new CreateCallForPaper("My conference 2", LocalDate.parse("2021-01-01"), "url", "andrzej");
    execute(componentClient.forValueEntity(cfpId2).call(CallForPaperEntity::create).params(callForPaper2));

    String cfpId3 = "3";
    CreateCallForPaper callForPaper3 = new CreateCallForPaper("My conference 3", LocalDate.parse("2021-01-02"), "url", "andrzej");
    execute(componentClient.forValueEntity(cfpId3).call(CallForPaperEntity::create).params(callForPaper3));

    await()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        //when
        ResponseEntity<String> response =
          webClient
            .post()
            .uri("/api/cfp/list")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .bodyValue("token=" + token)
            .retrieve()
            .toEntity(String.class)
            .block(timeout);

        //then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Payload payload = gson.fromJson(response.getBody(), Payload.class);
        RichTextBlock richTextBlock = (RichTextBlock) payload.getBlocks().get(0);
        RichTextListElement richTextElements = (RichTextListElement) richTextBlock.getElements().get(1);
        List<String> conferenceNames = richTextElements.getElements().stream()
          .map(el -> (RichTextSectionElement) el)
          .map(el -> (RichTextSectionElement.Link) el.getElements().get(0))
          .map(link -> link.getText())
          .limit(3) //avoid data from other tests
          .toList();

        assertThat(conferenceNames).containsExactly("My conference 2", "My conference 3", "My conference 1");
      });
  }

  private <T> T execute(DeferredCall<Any, T> deferredCall) {
    try {
      return deferredCall.execute().toCompletableFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

}