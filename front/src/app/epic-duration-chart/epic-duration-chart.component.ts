import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EChartsOption } from 'echarts';
import { NgxEchartsModule, NGX_ECHARTS_CONFIG } from 'ngx-echarts';

import { JiraService } from '../../service/jira.service';
import { EpicDeliveryOverview } from '../../model/epic-duration.model';

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

  isLoading = false;
  data: EpicDeliveryOverview[] = [];
  displayedColumns: string[] = ['epicKey', 'epicSummary', 'status', 'versions', 'teamsAndSprints'];
  chartOptions: EChartsOption = {};

  constructor(private jiraService: JiraService) {}

  ngOnInit(): void {
    this.loadData();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['projectKey'] && this.projectKey) {
      this.loadData();
    }
  }

  loadData(): void {
    this.isLoading = true;
    this.jiraService.getEpicDeliveriesByTeam(this.projectKey).subscribe({
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

  formatVersions(row: EpicDeliveryOverview): string {
    return row.versionNames.join(', ');
  }

  formatTeamsAndSprints(row: EpicDeliveryOverview): string {
    const grouped: Record<string, string[]> = {};
    for (const delivery of row.sprintDeliveries) {
      grouped[delivery.teamName] = grouped[delivery.teamName] || [];
      grouped[delivery.teamName].push(delivery.sprintName);
    }

    return Object.entries(grouped)
      .map(([team, sprints]) => `${team}: ${sprints.join(' / ')}`)
      .join(' | ');
  }

  private buildChart(rows: EpicDeliveryOverview[]): void {
    const epics = rows.map(r => r.epicKey);
    const teams = Array.from(new Set(rows.flatMap(r => r.sprintDeliveries.map(d => d.teamName))));

    const series: any[] = teams.map(team => ({
      name: team,
      type: 'bar',
      stack: 'total',
      data: rows.map(row => row.sprintDeliveries.filter(d => d.teamName === team).length)
    }));

    this.chartOptions = {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { type: 'scroll' },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: epics, axisLabel: { rotate: 35 } },
      yAxis: { type: 'value', name: 'Nombre de sprints de livraison' },
      series
    };
  }
}
