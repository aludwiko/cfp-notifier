package io.kalix.application;

import akka.Done;
import com.typesafe.config.Config;
import io.kalix.domain.CallForPaper;
import io.kalix.domain.CallForPaperReminder;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

import static io.kalix.application.DurationCalculator.calculateDuration;
import static java.util.Arrays.stream;

public class ScheduleNotification extends Action {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final Config config;
  private final Clock clock;
  private final ComponentClient componentClient;

  public ScheduleNotification(Config config, Clock clock, ComponentClient componentClient) {
    this.config = config;
    this.clock = clock;
    this.componentClient = componentClient;
  }

  @Subscribe.ValueEntity(CallForPaperEntity.class)
  public Effect<Done> onChange(CallForPaper callForPaper) {
    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, clock.getZone());
    List<CompletionStage<Done>> timersSchedules = getIntervals().stream().filter(applicableIntervals(callForPaper, today)).map(howManyDaysBefore -> {
      logger.info("Scheduling notification for cfp: {} {} days before {}", callForPaper, howManyDaysBefore, callForPaper.deadline());
      return timers().startSingleTimer(
        timerName(howManyDaysBefore, callForPaper.id()),
        calculateDuration(now, howManyDaysBefore, callForPaper.deadline(), clock),
        componentClient.forAction().call(Notify::runNotification).params(CallForPaperReminder.of(callForPaper, howManyDaysBefore)));
    }).toList();

    return effects().asyncReply(
      CompletableFuture.allOf(timersSchedules.toArray(new CompletableFuture<?>[0]))
        .thenApply(__ -> Done.getInstance())
    );
  }

  @Subscribe.ValueEntity(value = CallForPaperEntity.class, handleDeletes = true)
  public Effect<Done> onDelete() {
    return effects().asyncReply(actionContext().metadata().asCloudEvent().subject().map(cfpId -> {
      logger.info("Deleting scheduled notifications for cfp: {}", cfpId);
      return timers().cancel(timerName(7, cfpId)).thenCompose(__ ->
        timers().cancel(timerName(1, cfpId)));
    }).orElse(CompletableFuture.completedStage(Done.getInstance())));
  }

  private static String timerName(Integer interval, String cfpId) {
    return "notifyAboutCfp-" + cfpId + "-" + interval;
  }

  private Predicate<Integer> applicableIntervals(CallForPaper callForPaper, LocalDate today) {
    return interval -> callForPaper.deadline().minusDays(interval).isAfter(today);
  }

  private List<Integer> getIntervals() {
    String intervalsStr = config.getString("cfp.notifier.notification-intervals");
    if (intervalsStr == null || intervalsStr.isEmpty()) {
      return List.of(7, 1);
    } else {
      return stream(intervalsStr.split(",")).map(Integer::parseInt).toList();
    }
  }
}
