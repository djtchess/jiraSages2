import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EChartsOption } from 'echarts';
import { NgxEchartsModule, NGX_ECHARTS_CONFIG } from 'ngx-echarts';

import { JiraService } from '../../service/jira.service';
import { SprintVersionEpicDuration } from '../../model/epic-duration.model';

@Component({
  selector: 'app-epic-duration-chart',
  standalone: true,
  imports: [CommonModule, NgxEchartsModule, MatTableModule, MatProgressSpinnerModule],
  providers: [
    {
      provide: NGX_ECHARTS_CONFIG,
      useFactory: () => ({ echarts: () => import('echarts') })
    }
  ],
  templateUrl: './epic-duration-chart.component.html',
  styleUrl: './epic-duration-chart.component.css'
})
export class EpicDurationChartComponent implements OnInit, OnChanges {
  @Input() projectKey = 'SAG';
  @Input() boardId = 6;

  isLoading = false;
  data: SprintVersionEpicDuration[] = [];
  displayedColumns: string[] = ['sprintName', 'versionName', 'epicCount', 'totalDurationDays', 'averageDurationDays'];
  chartOptions: EChartsOption = {};

  constructor(private jiraService: JiraService) {}

  ngOnInit(): void {
    this.loadData();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['projectKey'] || changes['boardId']) && this.projectKey && this.boardId) {
      this.loadData();
    }
  }

  loadData(): void {
    this.isLoading = true;
    this.jiraService.getEpicDurationsBySprintAndVersion(this.projectKey, this.boardId).subscribe({
      next: (rows) => {
        this.data = rows;
        this.buildChart(rows);
        this.isLoading = false;
      },
      error: () => {
        this.data = [];
        this.chartOptions = {};
        this.isLoading = false;
      }
    });
  }

  private buildChart(rows: SprintVersionEpicDuration[]): void {
    const sprintNames = Array.from(new Set(rows.map(r => r.sprintName)));
    const versions = Array.from(new Set(rows.map(r => r.versionName)));

    const series: any[] = versions.map(version => ({
      name: version,
      type: 'bar',
      stack: 'total',
      emphasis: { focus: 'series' },
      data: sprintNames.map(sprint => {
        const row = rows.find(r => r.sprintName === sprint && r.versionName === version);
        return row?.averageDurationDays ?? 0;
      })
    }));

    this.chartOptions = {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' }
      },
      legend: {
        type: 'scroll'
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'value',
        name: 'Durée moyenne (jours)'
      },
      yAxis: {
        type: 'category',
        data: sprintNames
      },
      series
    };
  }
}
