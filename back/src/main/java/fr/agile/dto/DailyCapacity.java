package fr.agile.dto;

import java.time.LocalDate;

public record DailyCapacity(double jh, double capacite, double velocity, LocalDate jour) { }
