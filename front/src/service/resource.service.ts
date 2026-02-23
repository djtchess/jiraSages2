import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Resource, Event } from '../model/Resource';

@Injectable({
  providedIn: 'root'
})
export class ResourceService {

  private http = inject(HttpClient);
  private resources = signal<Resource[]>([])
  readonly urlDevelopper = 'http://localhost:8088/api/developpers';

  constructor() { 
    console.log("constructor ResourceService appelé");
  }

  getResources(): Observable<Resource[]> {
    return this.http.get<Resource[]>(this.urlDevelopper).pipe(
      tap(resources => this.resources.set(resources))
    );
  }

  saveResource(resource: Resource): Observable<Resource> {
    return this.http.post<Resource>(this.urlDevelopper, resource).pipe(
      tap(savedResource => {
        this.resources.update(res => [...res, savedResource]);
      })
    );
  }


  // saveEvent(event: any): Observable<Event> {
  //   console.log("post EventService by resource appelé ");
  //   console.log("event.dateDebutEvent :  "+event.dateDebutEvent);
  //   return this.http.post<Event>(this.urlDevelopper+'/create', event).pipe(
  //     tap(savedEvent => {
  //       console.log("post saved ok appelé "+savedEvent.dateDebutEvent);
  //     })
  //   );
  // }
  
}
