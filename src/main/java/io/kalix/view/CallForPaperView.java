package io.kalix.view;

import io.kalix.domain.CallForPaper;

import java.time.LocalDate;

public record CallForPaperView(String id, String conferenceName, String deadline, long deadlineInDays, String conferenceLink) {

  public static CallForPaperView of(CallForPaper callForPaper) {
    return new CallForPaperView(callForPaper.id(), callForPaper.conferenceName(), callForPaper.deadline().toString(), callForPaper.deadline().toEpochDay(), callForPaper.conferenceLink());
  }
}

