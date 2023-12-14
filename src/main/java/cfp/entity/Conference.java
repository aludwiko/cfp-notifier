package cfp.entity;

import java.time.LocalDate;

public record Conference(String id, String name, LocalDate deadline) {}

