import { Component, inject, OnInit, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { SprintInfo } from '../../model/SprintInfo.model';

import { JiraService } from '../../service/jira.service';
import { BurnupChartComponent } from '../burnup-chart/burnup-chart.component';

import { MatPaginatorModule } from '@angular/material/paginator';
import { CommonModule } from '@angular/common';
import { NgIf } from '@angular/common';
import { MatIcon, MatIconModule } from '@angular/material/icon';
import { MatButtonModule, MatIconButton } from '@angular/material/button';
import { SprintScopeComponent } from '../sprint-scope/sprint-scope.component';
import { Router } from '@angular/router';

@Component({
  selector: 'app-sprint-list',
  standalone: true,
  imports: [
    MatPaginatorModule,
    MatTableModule,
    MatSortModule,
    MatIcon,
    CommonModule,
    BurnupChartComponent,
    SprintScopeComponent,
    MatButtonModule,
    MatIconModule,
    NgIf
  ],
  templateUrl: './sprint-list.component.html',
  styleUrl: './sprint-list.component.css'
})
export class SprintListComponent implements OnInit {

  displayedColumns: string[] = ['id', 'name', 'state', 'startDate', 'endDate', 'action', 'scope'];

  dataSource = new MatTableDataSource<SprintInfo>([]);
  selectedSprintId: string | null = null;
  selectedSprintName: string | null = null;
  selectedSprint!: SprintInfo;

  selectedScopeSprintId: string | null = null;
  selectedScopeSprintName: string | null = null;


  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;
  
  private router = inject(Router);

  constructor(private jiraService: JiraService) {}

  ngOnInit(): void {
    this.jiraService.getSprintsForProject('SAG').subscribe(sprints => {
      this.dataSource.data = sprints;
      this.dataSource.paginator = this.paginator;
      this.dataSource.sort = this.sort;
    });
  }

  openBurnUp(sprint: SprintInfo): void {
    this.selectedSprintId = sprint.id.toString();
    this.selectedSprintName = sprint.name;
     this.selectedSprint = sprint;
  }

  closeBurnUp(): void {
    this.selectedSprintId = null;
    this.selectedSprintName = null;
  }

  toggleScope(sprint: SprintInfo): void {
    if (this.selectedScopeSprintId === sprint.id.toString()) {
      this.closeScope();
    } else {
      this.selectedScopeSprintId = sprint.id.toString();
      this.selectedScopeSprintName = sprint.name;
    }
  }

  closeScope(): void {
    this.selectedScopeSprintId = null;
    this.selectedScopeSprintName = null;
  }


  openCapacityView(boardId: number, nextSprintId: number): void {
    this.router.navigate(['/boards', boardId, 'sprints', nextSprintId, 'capacity']);
  }
}
