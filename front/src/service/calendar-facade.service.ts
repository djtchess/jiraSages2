import { Injectable } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { Holiday, Resource, Event } from '../model/Resource';
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

  saveEvent(payload: unknown): Observable<Event> {
    return this.eventService.saveEvent(payload);
  }

  deleteEvent(eventId: number): Observable<void> {
    return this.eventService.deleteEvent(eventId);
  }

  private normalizeResources(resources: Resource[]): Resource[] {
    return resources.map((resource) => {
      resource.events = resource.events.map((event) => ({
        ...event,
        dateDebutEvent: new Date(`${event.dateDebutEvent}T00:00:00`),
        dateFinEvent: new Date(`${event.dateFinEvent}T00:00:00`)
      }));

      resource.prenomResource = resource.prenomResource.replace('Assih Jean-Samuel', 'Jean-Samuel');
      return resource;
    });
  }
}
