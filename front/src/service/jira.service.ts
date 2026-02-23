import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SprintCommitInfo, SprintInfo, SprintKpiInfo } from '../model/SprintInfo.model';
import { SprintVersionEpicDuration } from '../model/epic-duration.model';


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

  getEpicDurationsBySprintAndVersion(projectKey: string, boardId: number): Observable<SprintVersionEpicDuration[]> {
    return this.http.get<SprintVersionEpicDuration[]>(
      `${this.baseUrl}/jira/projects/${projectKey}/boards/${boardId}/epics/durations`
    );
  }

}

