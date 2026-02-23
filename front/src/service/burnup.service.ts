import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface BurnupPoint {
  date: string;
  donePoints: number;
  capacity: number;
  capacityJH : number;
  velocity: number;
}

export interface BurnupData {
  totalStoryPoints: number;
  points: BurnupPoint[];
  totalJH: number;
  velocity: number;
}

@Injectable({ providedIn: 'root' })
export class BurnupService {
  private baseUrl = 'http://localhost:8088/api';

  constructor(private http: HttpClient) {}

  getBurnupData(sprintId: string): Observable<BurnupData> {
    return this.http.get<BurnupData>(`${this.baseUrl}/jira/burnup/${sprintId}`);
  }

  getSprintCapacity(sprintId: string): Observable<{ [key: string]: number }> {
    return this.http.get<{ [key: string]: number }>(`${this.baseUrl}/capacite/par-date/${sprintId}`,
      { responseType: 'json' as const } // Important pour éviter l'erreur de parsing
    );
  }

}

