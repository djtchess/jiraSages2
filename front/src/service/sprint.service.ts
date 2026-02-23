import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SprintInfo } from '../model/SprintInfo.model';


@Injectable({ providedIn: 'root' })
export class SprintService {
    private baseUrl = 'http://localhost:8088/api';
  private apiUrl = '/sprints';

  constructor(private http: HttpClient) {}

  createSprint(sprint: SprintInfo): Observable<SprintInfo> {
    return this.http.post<SprintInfo>(this.baseUrl+this.apiUrl, sprint);
  }

  getSprint(id: number): Observable<SprintInfo> {
    return this.http.get<SprintInfo>(`${this.baseUrl+this.apiUrl}/${id}`);
  }
}
