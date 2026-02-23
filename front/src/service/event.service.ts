import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Event } from '../model/Resource';

@Injectable({
  providedIn: 'root'
})
export class EventService {

  private http = inject(HttpClient);
  private events = signal<Event[]>([])
  readonly url = 'http://localhost:8088/api/events';

  constructor() { 
    console.log("constructor EventService appelé");
  }

  saveEvent(event: any): Observable<Event> {
    console.log("post EventService by resource appelé ");
    console.log("event.dateDebutEvent :  "+event.dateDebutEvent);
    return this.http.post<Event>(this.url+'/create', event).pipe(
      tap(savedEvent => {
        console.log("post saved ok appelé "+savedEvent.dateDebutEvent);
      })
    );
  }

  deleteEvent(id: number): Observable<void> {
    console.log('delete EventService appelé id=' + id);
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
