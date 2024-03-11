package io.kalix.view;

import io.kalix.domain.CallForPaper;
import io.kalix.application.CallForPaperEntity;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Table("cfp_all")
@ViewId("cfp-view-2")
public class AllCallForPaperView extends View<CallForPaperView> {

  @Subscribe.ValueEntity(CallForPaperEntity.class)
  public UpdateEffect<CallForPaperView> onChange(CallForPaper callForPaper) {
    return effects().updateState(CallForPaperView.of(callForPaper));
  }

  @Subscribe.ValueEntity(value = CallForPaperEntity.class, handleDeletes = true)
  public UpdateEffect<CallForPaperView> onDelete() {
    return effects().deleteState();
  }

  @GetMapping("/cfps")
  @Query("SELECT * as callForPaperViews FROM cfp_all ORDER BY deadlineInEpochDays ASC")
  public CallForPaperList getCallForPapers() {
    return null;
  }

  @GetMapping("/open-cfps")
  @Query("SELECT * as callForPaperViews FROM cfp_all WHERE deadlineInEpochDays >= :nowEpochDays ORDER BY deadlineInEpochDays ASC")
  public CallForPaperList getOpenCallForPapers(@RequestParam long nowEpochDays) {
    return null;
  }
}
