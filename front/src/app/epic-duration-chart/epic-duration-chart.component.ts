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
  displayedColumns: string[] = ['epicKey', 'epicSummary', 'status', 'versions', 'sprints'];
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
    this.jiraService.getEpicDeliveries(this.projectKey).subscribe({
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

  formatSprints(row: EpicDeliveryOverview): string {
    return row.sprintDeliveries.map(s => s.sprintName).join(' / ');
  }

  private buildChart(rows: EpicDeliveryOverview[]): void {
    const epics = rows.map(r => r.epicKey);
    const sprintCount = rows.map(r => r.sprintDeliveries.length);
    const versionCount = rows.map(r => r.versionNames.length);

    this.chartOptions = {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: any) => {
          const idx = params?.[0]?.dataIndex ?? 0;
          const epic = rows[idx];
          const versions = epic?.versionNames?.join(', ') ?? 'Sans version';
          const sprints = epic?.sprintDeliveries?.map(s => s.sprintName).join(' / ') ?? '-';
          const lines = (params as any[]).map(p => `${p.marker} ${p.seriesName}: <b>${p.value}</b>`).join('<br/>');
          return `<b>${epic?.epicKey ?? ''}</b><br/>${lines}<br/>Versions: <b>${versions}</b><br/>Sprints: <b>${sprints}</b>`;
        }
      },
      legend: { data: ['Nb sprints', 'Nb versions'] },
      grid: { left: '3%', right: '4%', bottom: '8%', containLabel: true },
      xAxis: { type: 'category', data: epics, axisLabel: { rotate: 35 } },
      yAxis: { type: 'value', name: 'Nombre' },
      series: [
        { name: 'Nb sprints', type: 'bar', data: sprintCount },
        { name: 'Nb versions', type: 'bar', data: versionCount }
      ]
    };
  }
}
