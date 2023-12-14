package cfp.entity;

import java.time.ZonedDateTime;

public record CreateConference(String name, ZonedDateTime deadline) {
}
