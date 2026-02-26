import { Injectable } from '@angular/core';
import { Observable, forkJoin, map, of } from 'rxjs';
import { Event, Holiday, Resource } from '../model/Resource';
import { CalendarEvent, CalendarEventDto, SaveCalendarEventPayload } from '../model/calendar-event.model';
import { EventService } from './event.service';
import { HolidayService } from './HolidayService';
import { ResourceService } from './resource.service';

export interface CalendarInitialData {
  resources: Resource[];
  holidays: Holiday[];
}

@Injectable({ providedIn: 'root' })
export class CalendarFacadeService {
  constructor(
    private readonly resourceService: ResourceService,
    private readonly eventService: EventService,
    private readonly holidayService: HolidayService
  ) {}

  loadInitialData(year: number): Observable<CalendarInitialData> {
    return forkJoin({
      resources: this.resourceService.getResources(),
      holidays: of(this.holidayService.getHolidays(year))
    }).pipe(
      map(({ resources, holidays }) => ({
        resources: this.normalizeResources(resources),
        holidays
      }))
    );
  }

  saveEvent(payload: SaveCalendarEventPayload): Observable<Event> {
    return this.eventService.saveEvent(payload).pipe(map((event) => this.toEventModel(event)));
  }

  deleteEvent(eventId: number): Observable<void> {
    return this.eventService.deleteEvent(eventId);
  }

  private normalizeResources(resources: Resource[]): Resource[] {
    return resources.map((resource) => {
      const normalizedEvents = resource.events
        .map((event) => this.mapResourceEventDto(event as CalendarEventDto))
        .filter((event): event is CalendarEvent => event !== null)
        .map((event) => this.toEventModel(event));

      return {
        ...resource,
        prenomResource: resource.prenomResource.replace('Assih Jean-Samuel', 'Jean-Samuel'),
        events: normalizedEvents
      };
    });
  }

  private mapResourceEventDto(dto: CalendarEventDto): CalendarEvent | null {
    const eventId = dto.idEvent ?? dto.id;
    if (!eventId || !dto.dateDebutEvent || !dto.dateFinEvent) {
      return null;
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

  private toEventModel(event: CalendarEvent): Event {
    return {
      idEvent: event.id,
      dateDebutEvent: event.dateDebutEvent,
      dateFinEvent: event.dateFinEvent,
      isJournee: event.isJournee,
      isMatin: event.isMatin,
      isApresMidi: event.isApresMidi
    };
  }

  private normalizeDate(value: string | Date): Date {
    const date = new Date(value);
    date.setHours(0, 0, 0, 0);
    return date;
  }
}
