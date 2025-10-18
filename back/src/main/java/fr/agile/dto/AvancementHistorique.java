package fr.agile.dto;

public class AvancementHistorique {
    private String date;
    private String field;  // nouveau champ pour le nom du champ modifié (ex: "status", "avancement")
    private String from;
    private String to;

    public AvancementHistorique() {}

    public AvancementHistorique(String date, String field, String from, String to) {
        this.date = date;
        this.field = field;
        this.from = from;
        this.to = to;
    }

    public String getDate() {
        return date;
    }

    public String getField() {
        return field;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setField(String field) {
        this.field = field;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setTo(String to) {
        this.to = to;
    }

    @Override
    public String toString() {
        return "AvancementHistorique{" +
                "date='" + date + '\'' +
                ", field='" + field + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                '}';
    }
}
