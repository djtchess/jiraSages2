import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SprintCommitInfo, SprintInfo, SprintKpiInfo } from '../model/SprintInfo.model';


@Injectable({ providedIn: 'root' })
export class JiraService {

  private baseUrl = 'http://localhost:8088/api';
  constructor(private http: HttpClient) {}

  getSprintsForProject(projectKey: string): Observable<SprintInfo[]> {
    return this.http.get<SprintInfo[]>(`${this.baseUrl}/jira/projects/${projectKey}/sprints`,
      { responseType: 'json' as const } // Important pour éviter l'erreur de parsing
    );
  }

  getSprintFullInfo(sprintId: string): Observable<SprintInfo> {
    return this.http.get<SprintInfo>(`${this.baseUrl}/jira/sprints/${sprintId}/full-info`);
  }


}
