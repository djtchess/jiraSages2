import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../app/core/api.tokens';
import { EpicDeliveryOverview } from '../model/epic-duration.model';
import { SprintInfo } from '../model/SprintInfo.model';

@Injectable({ providedIn: 'root' })
export class JiraService {
  constructor(
    private readonly http: HttpClient,
    @Inject(API_BASE_URL) private readonly baseUrl: string
  ) {}

  getSprintsForProject(projectKey: string): Observable<SprintInfo[]> {
    return this.http.get<SprintInfo[]>(`${this.baseUrl}/jira/projects/${projectKey}/sprints`, {
      responseType: 'json' as const
    });
  }

  getSprintFullInfo(sprintId: string): Observable<SprintInfo> {
    return this.http.get<SprintInfo>(`${this.baseUrl}/jira/sprints/${sprintId}/full-info`);
  }

  getEpicDeliveries(projectKey: string): Observable<EpicDeliveryOverview[]> {
    return this.http.get<EpicDeliveryOverview[]>(`${this.baseUrl}/jira/projects/${projectKey}/epics/deliveries`);
  }
}
