package io.kalix.view;

import io.kalix.domain.CallForPaper;

public record CallForPaperView(String id, String conferenceName, String deadline, long deadlineInEpochDays, String conferenceLink) {

  public static CallForPaperView of(CallForPaper callForPaper) {
    return new CallForPaperView(callForPaper.id(), callForPaper.conferenceName(), callForPaper.deadline().toString(), callForPaper.deadline().toEpochDay(), callForPaper.conferenceLink());
  }
}

