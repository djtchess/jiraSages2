// src/app/service/calendar.service.ts
import { Injectable } from '@angular/core';
import { HolidayService } from './HolidayService';
import { Resource } from '../model/Resource';
import { DateCalendar } from '../app/calendar/calendar.component';


export interface EventSpan {
  date: DateCalendar;
  colspan: number;
}

@Injectable({ providedIn: 'root' })
export class CalendarService {
  constructor(private holidayService: HolidayService) {}

  /* ------------------------------------------------------------------ */
  /* 📅 Dates util                                                       */
  /* ------------------------------------------------------------------ */
  getDatesOfMonth(year: number, month: number): DateCalendar[] {
    const daysInMonth = new Date(year, month, 0).getDate();
    const holidays = this.holidayService.getHolidays(year);
    const dates: DateCalendar[] = [];
    for (let day = 1; day <= daysInMonth; day++) {
      dates.push(new DateCalendar(holidays, year, month - 1, day));
    }
    return dates;
  }

  isResourceInactiveWholeMonth(resource: Resource, dates: DateCalendar[]): boolean {
    return dates.every((d) => d.isInactif(resource));
  }

  /* ------------------------------------------------------------------ */
  /* 🆕 Spans pour UN mois (clé cache YYYY-MM)                            */
  /* ------------------------------------------------------------------ */
  buildSpansForMonth(dates: DateCalendar[], resources: Resource[]): Map<string, EventSpan[]> {
    const map = new Map<string, EventSpan[]>();

    for (const res of resources) {
      const spans: EventSpan[] = [];
      let i = 0;

      while (i < dates.length) {
        const current = dates[i];

        if (current.isEvent(res)) {
          let spanStart = i;
          let spanLen = 1;

          while (
            i + 1 < dates.length &&
            dates[i + 1].isEvent(res) &&
            this.isNextDay(dates[i], dates[i + 1])
          ) {
            spanLen++;
            i++;
          }

          // cellule principale
          spans.push({ date: current, colspan: spanLen });
          // cellules fusionnées (colspan 0) pour alignement
          for (let j = 1; j < spanLen; j++) {
            spans.push({ date: dates[spanStart + j], colspan: 0 });
          }

          i++; // avance au jour suivant après la séquence
        } else {
          spans.push({ date: current, colspan: 1 });
          i++;
        }
      }

      map.set(res.prenomResource, spans);
    }

    return map;
  }

  /* ------------------------------------------------------------------ */
  /* 🌍 Spans pour PLUSIEURS mois (héritage ancien)                       */
  /* ------------------------------------------------------------------ */
  buildEventSpans(calVos: { dates: DateCalendar[] }[], resources: Resource[]): Map<string, Map<number, EventSpan[]>> {
    const eventSpansMap = new Map<string, Map<number, EventSpan[]>>();

    for (const res of resources) {
      const resMap = new Map<number, EventSpan[]>();
      calVos.forEach((vo, idx) => {
        const monthSpans = this.buildSpansForMonth(vo.dates, [res]).get(res.prenomResource)!;
        resMap.set(idx, monthSpans);
      });
      eventSpansMap.set(res.prenomResource, resMap);
    }
    return eventSpansMap;
  }

  /* ------------------------------------------------------------------ */
  /* 🔹 Helper                                                           */
  /* ------------------------------------------------------------------ */
  private isNextDay(d1: DateCalendar, d2: DateCalendar): boolean {
    return d2.getTime() - d1.getTime() === 86_400_000; // 24h en ms
  }
}
