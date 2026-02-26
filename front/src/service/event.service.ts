import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../app/core/api.tokens';
import { CalendarEvent, CalendarEventDto, SaveCalendarEventPayload } from '../model/calendar-event.model';

@Injectable({
  providedIn: 'root'
})
export class EventService {
  private readonly url: string;

  constructor(
    private readonly http: HttpClient,
    @Inject(API_BASE_URL) apiBaseUrl: string
  ) {
    this.url = `${apiBaseUrl}/events`;
  }

  saveEvent(payload: SaveCalendarEventPayload): Observable<CalendarEvent> {
    return this.http
      .post<CalendarEventDto>(`${this.url}/create`, payload)
      .pipe(map((eventDto) => this.mapCalendarEventDto(eventDto)));
  }

  deleteEvent(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  private mapCalendarEventDto(dto: CalendarEventDto): CalendarEvent {
    const eventId = dto.idEvent ?? dto.id;
    if (!eventId || !dto.dateDebutEvent || !dto.dateFinEvent) {
      throw new Error('Invalid CalendarEventDto received from backend');
    }

    return {
      id: eventId,
      dateDebutEvent: this.normalizeDate(dto.dateDebutEvent),
      dateFinEvent: this.normalizeDate(dto.dateFinEvent),
      isJournee: !!dto.isJournee,
      isMatin: !!dto.isMatin,
      isApresMidi: !!dto.isApresMidi
    };
  }

  private normalizeDate(value: string | Date): Date {
    const date = new Date(value);
    date.setHours(0, 0, 0, 0);
    return date;
  }
}
