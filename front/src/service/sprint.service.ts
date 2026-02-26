import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../app/core/api.tokens';
import { SprintInfo } from '../model/SprintInfo.model';

@Injectable({ providedIn: 'root' })
export class SprintService {
  private readonly apiUrl = '/sprints';

  constructor(
    private readonly http: HttpClient,
    @Inject(API_BASE_URL) private readonly baseUrl: string
  ) {}

  createSprint(sprint: SprintInfo): Observable<SprintInfo> {
    return this.http.post<SprintInfo>(`${this.baseUrl}${this.apiUrl}`, sprint);
  }

  getSprint(id: number): Observable<SprintInfo> {
    return this.http.get<SprintInfo>(`${this.baseUrl}${this.apiUrl}/${id}`);
  }
}
