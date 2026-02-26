import {
  Component,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgxEchartsModule, NGX_ECHARTS_CONFIG } from 'ngx-echarts';
import { EChartsOption } from 'echarts';
import { finalize } from 'rxjs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { BurnupService } from '../../service/burnup.service';
import { SprintService } from '../../service/sprint.service';
import { SprintInfo } from '../../model/SprintInfo.model';
import * as echarts from 'echarts';

@Component({
  selector: 'app-burnup-chart',
  standalone: true,
  imports: [CommonModule, NgxEchartsModule, MatProgressSpinnerModule],
  providers: [
    {
      provide: NGX_ECHARTS_CONFIG,
      useFactory: () => ({ echarts: () => import('echarts') }),
    },
  ],
  templateUrl: './burnup-chart.component.html',
  styleUrl: './burnup-chart.component.css',
})
export class BurnupChartComponent implements OnChanges, OnInit, OnDestroy {
  @Input() sprintId!: string;
  @Input() sprintName!: string;
  @Input() sprint!: SprintInfo;

  chartOptions: EChartsOption = {};
  isLoading = true;

  sprintInfo: SprintInfo = {
    id: 0,
    name: '',
    state: '',
    startDate: '',
    endDate: '',
    completeDate: '',
    velocity: 0,
    velocityStart: 0,
    originBoardId: '',
    burnupData: { velocity: 0, totalJH: 0, totalStoryPoints: 0, points: [] },
    sprintCommitInfo: { committedAtStart: [], addedDuring: [], removedDuring: [] },
    sprintKpiInfo: {
      totalTickets: 0, committedAtStart: 0, committedAndDone: 0, addedDuring: 0, removedDuring: 0,
      doneTickets: 0, devDoneBeforeSprint: 0, engagementRespectePourcent: 0, ajoutsNonPrevusPourcent: 0,
      nonTerminesEngagesPourcent: 0, succesGlobalPourcent: 0, pointsCommited: 0, pointsAdded: 0,
      pointsRemoved: 0, typeCountCommitted: {}, typeCountAdded: {}, typeCountAll: {}
    }
  };

  private chartInstance: echarts.ECharts | null = null;
  private themeObserver?: MutationObserver;
  private latestBurnupData: any;
  private burnupService = inject(BurnupService);
  private sprintService = inject(SprintService);

  ngOnInit(): void {
    this.themeObserver = new MutationObserver(() => {
      if (this.latestBurnupData) {
        this.buildChart(this.latestBurnupData);
      }
    });
    this.themeObserver.observe(document.body, { attributes: true, attributeFilter: ['class'] });
  }

  ngOnDestroy(): void {
    this.themeObserver?.disconnect();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['sprintId'] && this.sprintId) {
      this.loadBurnup(this.sprintId);
    }
    if (changes['sprint'] && this.sprint) {
      this.sprintInfo = { ...this.sprintInfo, ...this.sprint, burnupData: { ...this.sprintInfo.burnupData, ...this.sprint.burnupData } };
    }
  }

  createSprint(): void {
    if (!this.sprintInfo.burnupData.points?.length) {
      alert('Les données burn-up ne sont pas encore chargées.');
      return;
    }
    this.sprintService.createSprint({ ...this.sprintInfo }).subscribe();
  }

  loadBurnup(sprintId: string): void {
    this.isLoading = true;
    this.burnupService.getBurnupData(sprintId)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe((data) => {
        this.latestBurnupData = data;
        this.sprintInfo.burnupData.velocity = data.velocity ?? 0;
        this.sprintInfo.burnupData.totalJH = data.totalJH ?? 0;
        this.sprintInfo.burnupData.points = data.points;
        this.sprintInfo.burnupData.totalStoryPoints = data.totalStoryPoints;
        this.buildChart(data);
      });
  }

  private buildChart(data: any): void {
    const styles = getComputedStyle(document.body);
    const text = styles.getPropertyValue('--chart-label').trim() || '#4a5c82';
    const grid = styles.getPropertyValue('--chart-grid').trim() || '#d9e1f4';

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const dates = data.points.map((p: any) => p.date);
    const done = data.points.map((p: any) => {
      const pointDate = new Date(p.date);
      pointDate.setHours(0, 0, 0, 0);
      return pointDate <= today ? p.donePoints : null;
    });

    const velocities = data.points.map((p: any) => Number(p.velocity ?? 0));

    this.chartOptions = {
      textStyle: { color: text },
      tooltip: { trigger: 'axis' },
      legend: { data: ['Réalisé', 'Capacité', 'Charge totale', 'Vélocité (pts/JH)'], textStyle: { color: text } },
      grid: { left: '4%', right: '4%', bottom: '10%', containLabel: true },
      xAxis: { type: 'category', data: dates, axisLabel: { color: text }, axisLine: { lineStyle: { color: grid } } },
      yAxis: [
        { type: 'value', name: 'Points', nameTextStyle: { color: text }, axisLabel: { color: text }, splitLine: { lineStyle: { color: grid } } },
        { type: 'value', name: 'Vélocité', nameTextStyle: { color: text }, axisLabel: { color: text }, splitLine: { show: false } }
      ],
      series: [
        { name: 'Réalisé', type: 'line', data: done, smooth: true, symbol: 'circle', symbolSize: 7, lineStyle: { width: 3, color: '#35d6ff' }, itemStyle: { color: '#35d6ff' } },
        { name: 'Capacité', type: 'line', data: data.points.map((p: any) => p.capacity), smooth: true, symbol: 'none', lineStyle: { type: 'dotted', width: 2, color: '#7ca8ff' } },
        { name: 'Charge totale', type: 'line', data: data.points.map(() => data.totalStoryPoints), symbol: 'none', lineStyle: { type: 'dashed', width: 2, color: '#f26b88' } },
        { name: 'Vélocité (pts/JH)', type: 'bar', yAxisIndex: 1, data: velocities, barMaxWidth: 16, itemStyle: { color: 'rgba(126, 242, 255, 0.42)', borderColor: '#7ef2ff', borderWidth: 1 } }
      ]
    };

    this.chartInstance?.setOption(this.chartOptions, true);
  }

  onChartInit(ec: echarts.ECharts): void {
    this.chartInstance = ec;
    this.chartInstance.resize();
  }
}
