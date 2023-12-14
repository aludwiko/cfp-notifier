package io.kalix.application;

import java.time.Instant;
import java.time.LocalDate;

public record CreateCallForPaper(String conferenceName, LocalDate deadline, String conferenceLink, String userName) {
}
