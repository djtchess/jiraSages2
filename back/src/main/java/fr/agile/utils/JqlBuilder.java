package fr.agile.utils;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class JqlBuilder {

    /** Format yyyy-MM-dd (ex. 2025-06-16) utilisé par Jira pour les dates. */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final StringBuilder sb = new StringBuilder();

    public JqlBuilder project(String key) {
        sb.append("project = ").append(key).append(' ');
        return this;
    }

    public JqlBuilder and() {
        sb.append("AND ");
        return this;
    }

    public JqlBuilder sprintEquals(String id) {
        sb.append("sprint = ").append(id).append(' ');
        return this;
    }

    public JqlBuilder issuetypeIn(String... types) {
        sb.append("issuetype IN (")
                .append(Arrays.stream(types)
                        .map(t -> "\"" + t + "\"")
                        .collect(Collectors.joining(", ")))
                .append(") ");
        return this;
    }

    public JqlBuilder issuetypeIn(Collection<String> issuetypeIns) {
        return issuetypeIn(issuetypeIns.toArray(new String[0]));
    }

    public JqlBuilder statusIn(String... statuses) {
        sb.append("status IN (")
                .append(Arrays.stream(statuses)
                        .map(s -> "\"" + s + "\"")
                        .collect(Collectors.joining(", ")))
                .append(") ");
        return this;
    }

    public JqlBuilder statusIn(Collection<String> statuses) {
        return statusIn(statuses.toArray(new String[0]));
    }

    /* -------- NOUVELLE méthode assigneeNotIn ----------------------- */

    public JqlBuilder assigneeNotInOrEmpty(Collection<String> accounts) {
        sb.append("(assignee NOT IN (")
                .append(String.join(", ", accounts))
                .append(") OR assignee IS EMPTY) ");
        return this;
    }


    public JqlBuilder updatedBetween(LocalDate fromInclusive, LocalDate toExclusive) {
        sb.append("updated >= \"")
                .append(ISO.format(fromInclusive))
                .append("\" AND updated < \"")
                .append(ISO.format(toExclusive))
                .append("\" ");
        return this;
    }

    /* ——— JqlBuilder : nouvelle surcharge ——— */
    public JqlBuilder updatedBetween(ZonedDateTime fromIncl, ZonedDateTime toExcl) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        sb.append("updated >= \"").append(fmt.format(fromIncl)).append("\" ")
                .append("AND updated < \"").append(fmt.format(toExcl)).append("\" ");
        return this;
    }

    public JqlBuilder raw(String clause) {
        sb.append(clause).append(' ');
        return this;
    }

    public JqlBuilder orderBy(String clause) {
        sb.append("ORDER BY ").append(clause).append(' ');
        return this;
    }

    public String build() {
        return sb.toString().trim();
    }
}
