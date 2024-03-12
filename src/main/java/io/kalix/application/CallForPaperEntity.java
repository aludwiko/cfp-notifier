package io.kalix.application;

import io.kalix.domain.CallForPaper;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.valueentity.ValueEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static kalix.javasdk.StatusCode.ErrorCode.BAD_REQUEST;
import static kalix.javasdk.StatusCode.ErrorCode.NOT_FOUND;

@TypeId("call-for-paper")
@Id("id")
@RequestMapping("/cfp/{id}")
public class CallForPaperEntity extends ValueEntity<CallForPaper> {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final Clock clock;

  public CallForPaperEntity(Clock clock) {
    this.clock = clock;
  }

  @PostMapping
  public Effect<CallForPaper> create(@RequestBody CreateCallForPaper createCallForPaper) {
    if (currentState() != null) {
      return effects().error("Cfp already exists " + commandContext().entityId(), BAD_REQUEST);
    } else if (!isValid(createCallForPaper)) {
      logger.info("Invalid Cfp: {}", createCallForPaper);
      return effects().error("Invalid Cfp " + commandContext().entityId(), BAD_REQUEST);
    } else {
      var callForPaper = new CallForPaper(
        commandContext().entityId(),
        createCallForPaper.conferenceName(),
        createCallForPaper.deadline(),
        createCallForPaper.conferenceLink(),
        createCallForPaper.userName(),
        Instant.now(clock)
      );

      logger.info("Creating new Cfp: {}", callForPaper);
      return effects()
        .updateState(callForPaper)
        .thenReply(callForPaper);
    }
  }

  private boolean isValid(CreateCallForPaper createCallForPaper) {
    LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
    if (createCallForPaper.conferenceName() == null || createCallForPaper.conferenceName().isEmpty()) {
      return false;
    }
    if (createCallForPaper.deadline() == null) {
      return false;
    } else if (
      createCallForPaper.deadline().isAfter(today.plusDays(TimeUnit.SECONDS.toDays(21474835)))) {
      //see akka.actor.LightArrayRevolverScheduler#checkMaxDelay
      logger.info("Deadline too far in the future: {}", createCallForPaper.deadline());
      return false;
    }
    if (createCallForPaper.conferenceLink() == null || createCallForPaper.conferenceLink().isEmpty()) {
      return false;
    }
    return true;
  }

  @GetMapping
  public Effect<CallForPaper> get() {
    if (currentState() == null) {
      return effects().error("Cfp not found " + commandContext().entityId(), NOT_FOUND);
    } else {
      return effects().reply(currentState());
    }
  }

  @PatchMapping
  public Effect<String> delete() {
    if (currentState() == null) {
      return effects().error("Cfp not found " + commandContext().entityId(), NOT_FOUND);
    } else {
      logger.info("Deleting Cfp: {}", currentState());
      return effects().deleteEntity().thenReply("Deleted");
    }
  }
}