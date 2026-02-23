import { DateCalendar } from "../app/calendar/calendar.component";

export class CalendarVO {
  constructor(
    public month: number,
    public monthFR: string,
    public dates: DateCalendar[]
  ) {}

  /** nouvel attribut */
 dayLabels: string[] = [];
}