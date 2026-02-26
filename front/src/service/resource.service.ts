import { HttpClient } from '@angular/common/http';
import { Inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { API_BASE_URL } from '../app/core/api.tokens';
import { Resource } from '../model/Resource';

@Injectable({
  providedIn: 'root'
})
export class ResourceService {
  private readonly resources = signal<Resource[]>([]);
  private readonly urlDevelopper: string;

  constructor(
    private readonly http: HttpClient,
    @Inject(API_BASE_URL) apiBaseUrl: string
  ) {
    this.urlDevelopper = `${apiBaseUrl}/developpers`;
  }

  getResources(): Observable<Resource[]> {
    return this.http.get<Resource[]>(this.urlDevelopper).pipe(tap((resources) => this.resources.set(resources)));
  }

  saveResource(resource: Resource): Observable<Resource> {
    return this.http.post<Resource>(this.urlDevelopper, resource).pipe(
      tap((savedResource) => {
        this.resources.update((res) => [...res, savedResource]);
      })
    );
  }
}
