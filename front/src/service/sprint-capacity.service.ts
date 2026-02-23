import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { format } from 'date-fns';
import { CapaciteDevProchainSprintDTO } from '../model/capacite-dev-prochain-sprint.model';
import { Observable } from 'rxjs';

export interface SprintCapacity {
  idDevelopper: number;
  nom: string;
  prenom: string;
  capaciteStoryPoints: number;
}

@Injectable({ providedIn: 'root' })
export class SprintCapacityService {
   private baseUrl = 'http://localhost:8088/api';
 

  constructor(private http: HttpClient) {}

  getCapacity(start: Date, end: Date) {
    const startStr = format(start, 'yyyy-MM-dd');
    const endStr = format(end, 'yyyy-MM-dd');
    return this.http.get<SprintCapacity[]>(`${this.baseUrl}?start=${startStr}&end=${endStr}`);
  }

  getAllDevCapacityAndCarryover(boardId: number, nextSprintId: number): Observable<CapaciteDevProchainSprintDTO[]> {
    return this.http.get<CapaciteDevProchainSprintDTO[]>(
      `${this.baseUrl}/sprints/boards/${boardId}/sprints/${nextSprintId}/developers/capacity`
    );
  }
}