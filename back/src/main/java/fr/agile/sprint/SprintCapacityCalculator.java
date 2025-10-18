package fr.agile.sprint;


import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import fr.agile.entities.Developper;
import fr.agile.entities.Event;
import fr.agile.entities.JoursFeries;
import fr.agile.utils.BurnupUtils;

@Component
public class SprintCapacityCalculator {

    public record JourTravail(LocalDate jour, double chargeJournaliere) {
    }

    public List<JourTravail> calculerJoursTravailles(
            Developper dev,
            LocalDate dateDebutSprint,
            LocalDate dateFinSprint,
            List<JoursFeries> joursFeries,
            List<Event> evenements) {

        List<JourTravail> result = new ArrayList<>();
        int jourOuvreIndex = 0;

        for (LocalDate date = dateDebutSprint; !date.isAfter(dateFinSprint); date = date.plusDays(1)) {
            DayOfWeek jourSemaine = date.getDayOfWeek();

            // Exclure samedi/dimanche
            if (jourSemaine != DayOfWeek.SATURDAY && jourSemaine != DayOfWeek.SUNDAY) {
                LocalDate finalDate = date;
                boolean isFerie = joursFeries.stream().anyMatch(f -> f.getDateFerie().toLocalDate().equals(finalDate));

                if (!isFerie) {
                    double charge;

                    // Appliquer les règles spéciales selon le jour ouvré
                    if (jourOuvreIndex == 0 || jourOuvreIndex == 1) {
                        charge = 0.5;
                    } else if (date.equals(dateFinSprint)) {
                        charge = 0.0;
                    } else {
                        charge = 1.0;
                    }

                    // Récupérer les events pour ce jour pour ce développeur
                    for (Event ev : evenements) {
                        if (ev.getDevelopper().getId().equals(dev.getId()) &&
                                !date.isBefore(ev.getDateDebutEvent()) &&
                                !date.isAfter(ev.getDateFinEvent())) {

                            if (charge != 0 && (Boolean.TRUE.equals(ev.getIsMatin()) || Boolean.TRUE.equals(ev.getIsApresMidi()))) {
                                charge = 0.5;
                            } else {
                                charge = 0.0;
                            }
                        }
                    }

                    if ("Laurent".equals(dev.getPrenomDevelopper())) {
                        charge *= 0.50;
                    }

                    result.add(new JourTravail(date, BurnupUtils.roundToTwoDecimals(charge)));
                    jourOuvreIndex++; // Incrémenter seulement pour les jours ouvrés non fériés
                }
            }
        }

        return result;
    }

    public double calculerCapacite(List<JourTravail> joursTravail, double velociteParJour) {
        return joursTravail.stream()
                .mapToDouble(JourTravail::chargeJournaliere)
                .sum() * velociteParJour;
    }
}
