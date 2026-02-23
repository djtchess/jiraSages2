package fr.agile.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.agile.IssueChangelogData;
import fr.agile.dto.AvancementHistorique;
import fr.agile.dto.BoardInfo;
import fr.agile.dto.SprintInfoDTO;
import fr.agile.model.dto.Ticket;
import fr.agile.utils.BurnupUtils;

@Component
public class JiraParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter JIRA_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
    private static final DateTimeFormatter[] JIRA_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
    };

    public ObjectNode buildSearchJqlBody(String jql, int maxResults, String nextPageToken) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("jql", jql);
        body.put("maxResults", maxResults);
        if (nextPageToken != null) {
            body.put("nextPageToken", nextPageToken);
        }

        ArrayNode fieldsArray = body.putArray("fields");
        fieldsArray.add("key")
                .add("assignee")
                .add("status")
                .add("created")
                .add("fixVersions")
                .add("issuetype")
                .add("customfield_10028")
                .add("customfield_10126")
                .add("customfield_10020")
                .add("customfield_10242");
        return body;
    }

    public TicketPage parseTicketPage(String response, String jiraUrl) throws java.io.IOException {
        JsonNode rootNode = MAPPER.readTree(response);
        List<Ticket> tickets = new ArrayList<>();

        for (JsonNode issue : rootNode.path("issues")) {
            String key = issue.path("key").asText();
            JsonNode fields = issue.path("fields");

            String assignee = fields.path("assignee").isMissingNode() || fields.path("assignee").isNull()
                    ? "Non assigné"
                    : fields.path("assignee").path("displayName").asText();

            String storyPts = fields.path("customfield_10028").isNull() ? null : fields.path("customfield_10028").asText();
            String avancement = fields.path("customfield_10126").isNull() ? null : fields.path("customfield_10126").asText();

            String engagementSprint = null;
            JsonNode customField = fields.path("customfield_10242");
            if (customField.isArray() && customField.size() > 0) {
                engagementSprint = customField.get(0).path("value").asText(null);
            }

            String versionCorrigee = null;
            JsonNode fixVersionsNode = fields.path("fixVersions");
            if (fixVersionsNode.isArray() && fixVersionsNode.size() > 0) {
                versionCorrigee = fixVersionsNode.get(0).path("name").asText();
            }

            LocalDate created = LocalDate.parse(fields.path("created").asText().substring(0, 10));

            List<String> sprintIds = new ArrayList<>();
            JsonNode sprintField = fields.path("customfield_10020");
            if (sprintField.isArray()) {
                for (JsonNode s : sprintField) {
                    if (s.has("id")) {
                        sprintIds.add(s.path("id").asText());
                    }
                }
            }

            Ticket t = Ticket.builder()
                    .ticketKey(key)
                    .url(jiraUrl + "/browse/" + key)
                    .assignee(assignee)
                    .status(fields.path("status").path("name").asText("Inconnu"))
                    .storyPoints(BurnupUtils.parseDouble(storyPts))
                    .storyPointsRealiseAvantSprint(null)
                    .avancement(BurnupUtils.parseDouble(avancement))
                    .engagementSprint(engagementSprint)
                    .type(fields.path("issuetype").path("name").asText("Inconnu"))
                    .versionCorrigee(versionCorrigee)
                    .createdDate(created)
                    .sprintIds(sprintIds)
                    .build();
            tickets.add(t);
        }

        boolean isLast = rootNode.path("isLast").asBoolean(false);
        String nextPageToken = rootNode.path("nextPageToken").isMissingNode() ? null : rootNode.path("nextPageToken").asText(null);

        return new TicketPage(tickets, isLast, nextPageToken);
    }

    public ChangelogPage parseChangelogPage(String body) throws java.io.IOException {
        JsonNode root = MAPPER.readTree(body);

        Map<String, AvancementHistorique> avancementMap = new HashMap<>();
        NavigableMap<LocalDateTime, String> statusParDate = new TreeMap<>();
        List<AvancementHistorique> sprintHistories = new ArrayList<>();

        for (JsonNode history : root.path("values")) {
            String createdStr = history.path("created").asText();
            ZonedDateTime createdZdt = ZonedDateTime.parse(createdStr, JIRA_DATE_FORMATTER);

            for (JsonNode item : history.path("items")) {
                String field = item.path("field").asText("");
                switch (field.toLowerCase()) {
                    case "status" -> statusParDate.put(createdZdt.toLocalDateTime(), item.path("toString").asText());
                    case "avancement" -> avancementMap.put(createdStr, new AvancementHistorique(createdStr, "avancement", getTextSafe(item, "fromString"), getTextSafe(item, "toString")));
                    case "sprint" -> sprintHistories.add(new AvancementHistorique(createdStr, "Sprint", item.path("from").asText(null), item.path("to").asText(null)));
                    default -> {
                    }
                }
            }
        }

        List<AvancementHistorique> avancementHistorique = new ArrayList<>(avancementMap.values());
        avancementHistorique.sort(Comparator.comparing(a -> ZonedDateTime.parse(a.getDate(), JIRA_DATE_FORMATTER)));

        IssueChangelogData data = new IssueChangelogData(avancementHistorique, statusParDate, sprintHistories);
        return new ChangelogPage(data, root.path("total").asInt());
    }

    public SprintInfoDTO parseSprintInfo(String response, ZoneId zone) throws java.io.IOException {
        JsonNode sprintNode = MAPPER.readTree(response);

        String state = sprintNode.path("state").asText();
        String startDateStr = sprintNode.path("startDate").asText(null);
        String endDateStr = sprintNode.path("endDate").asText(null);
        String completeDateStr = sprintNode.path("completeDate").asText(null);

        SprintInfoDTO sprint = new SprintInfoDTO();
        sprint.setId(sprintNode.path("id").asLong());
        sprint.setName(sprintNode.path("name").asText());
        sprint.setState(state);
        sprint.setOriginBoardId(sprintNode.path("originBoardId").asLong());
        sprint.setStartDate(parseJiraDate(startDateStr, zone));
        sprint.setCompleteDate(parseJiraDate(completeDateStr, zone));
        sprint.setEndDate(resolveEffectiveEndDate(state, endDateStr, completeDateStr, zone));
        return sprint;
    }

    public BoardsPage parseBoardsPage(String response) throws java.io.IOException {
        JsonNode rootNode = MAPPER.readTree(response);
        List<BoardInfo> boards = new ArrayList<>();

        for (JsonNode boardNode : rootNode.path("values")) {
            BoardInfo board = new BoardInfo();
            board.setId(boardNode.path("id").asLong());
            board.setName(boardNode.path("name").asText());
            board.setType(boardNode.path("type").asText());
            boards.add(board);
        }
        return new BoardsPage(boards, rootNode.path("isLast").asBoolean());
    }

    public SprintsPage parseSprintsPage(String response, long boardId, ZoneId zone, ZonedDateTime filtreDate) throws java.io.IOException {
        JsonNode root = MAPPER.readTree(response);
        JsonNode values = root.path("values");
        boolean isLast = root.path("isLast").asBoolean(values.size() < 50);
        List<SprintInfoDTO> sprintList = new ArrayList<>();

        for (JsonNode sprintNode : values) {
            String state = sprintNode.path("state").asText();
            String startDateStr = sprintNode.path("startDate").asText(null);
            String endDateStr = sprintNode.path("endDate").asText(null);
            String completeDateStr = sprintNode.path("completeDate").asText(null);

            ZonedDateTime startDate = parseJiraDate(startDateStr, zone);

            SprintInfoDTO sprint = new SprintInfoDTO();
            sprint.setId(sprintNode.path("id").asLong());
            sprint.setName(sprintNode.path("name").asText());
            sprint.setState(state);
            sprint.setStartDate(startDate);
            sprint.setCompleteDate(parseJiraDate(completeDateStr, zone));
            sprint.setEndDate(resolveEffectiveEndDate(state, endDateStr, completeDateStr, zone));
            sprint.setOriginBoardId(boardId);

            if (startDate != null && startDate.isAfter(filtreDate)) {
                sprintList.add(sprint);
            }
        }

        return new SprintsPage(sprintList, isLast);
    }

    public ZonedDateTime resolveEffectiveEndDate(String state, String endDateStr, String completeDateStr, ZoneId zoneId) {
        ZonedDateTime endDate = parseJiraDate(endDateStr, zoneId);
        ZonedDateTime completeDate = parseJiraDate(completeDateStr, zoneId);

        if ("closed".equalsIgnoreCase(state) && completeDate != null) {
            return completeDate;
        }
        if ("active".equalsIgnoreCase(state) && endDate != null) {
            ZonedDateTime today = ZonedDateTime.now(zoneId);
            if (endDate.toLocalDate().isBefore(today.toLocalDate())) {
                return today;
            }
        }
        return endDate;
    }

    public ZonedDateTime parseJiraDate(String dateStr, ZoneId targetZone) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        for (DateTimeFormatter f : JIRA_FORMATS) {
            try {
                var odt = java.time.OffsetDateTime.parse(dateStr, f);
                return odt.atZoneSameInstant(targetZone);
            } catch (Exception ignore) {
            }
        }

        try {
            return java.time.Instant.parse(dateStr).atZone(targetZone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Format de date JIRA inattendu: " + dateStr, e);
        }
    }

    private String getTextSafe(JsonNode node, String path) {
        JsonNode child = node.path(path);
        return child.isMissingNode() || child.isNull() ? null : child.asText();
    }

    public record TicketPage(List<Ticket> tickets, boolean isLast, String nextPageToken) {
    }

    public record ChangelogPage(IssueChangelogData data, int total) {
    }

    public record BoardsPage(List<BoardInfo> boards, boolean isLast) {
    }

    public record SprintsPage(List<SprintInfoDTO> sprints, boolean isLast) {
    }
}
