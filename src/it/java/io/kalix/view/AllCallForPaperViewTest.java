package io.kalix.view;

import com.google.protobuf.any.Any;
import io.kalix.Main;
import io.kalix.application.CallForPaperEntity;
import io.kalix.application.CreateCallForPaper;
import kalix.javasdk.DeferredCall;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = Main.class)
@ActiveProfiles("it-test")
class AllCallForPaperViewTest extends KalixIntegrationTestKitSupport {

  @Test
  public void shouldListOnlyOpenCfps() {
    //given
    String cfpId1 = "1";
    CreateCallForPaper callForPaper1 = new CreateCallForPaper("My conference 1", LocalDate.now().minusDays(1), "url", "andrzej");
    execute(componentClient.forValueEntity(cfpId1).call(CallForPaperEntity::create).params(callForPaper1));

    String cfpId2 = "2";
    CreateCallForPaper callForPaper2 = new CreateCallForPaper("My conference 2", LocalDate.now().plusDays(1), "url", "andrzej");
    execute(componentClient.forValueEntity(cfpId2).call(CallForPaperEntity::create).params(callForPaper2));

    String cfpId3 = "3";
    CreateCallForPaper callForPaper3 = new CreateCallForPaper("My conference 3", LocalDate.now().plusDays(1), "url", "andrzej");
    execute(componentClient.forValueEntity(cfpId3).call(CallForPaperEntity::create).params(callForPaper3));


    await()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        CallForPaperList list = execute(componentClient.forView().call(AllCallForPaperView::getOpenCallForPapers).params(LocalDate.now().toEpochDay()));
        assertThat(list.callForPaperViews()).hasSize(2);
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