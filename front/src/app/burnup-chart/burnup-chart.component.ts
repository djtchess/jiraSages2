import {
  Component,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgxEchartsModule, NGX_ECHARTS_CONFIG } from 'ngx-echarts';
import { EChartsOption } from 'echarts';
import { Subscription, finalize } from 'rxjs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { BurnupService } from '../../service/burnup.service';
import { SprintService } from '../../service/sprint.service';
import { SprintInfo } from '../../model/SprintInfo.model';
import * as echarts from 'echarts';
import { ThemeService } from '../theme.service';

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
export class BurnupChartComponent implements OnChanges, OnDestroy {
  @Input() sprintId!: string;
  @Input() sprintName!: string;
  @Input() sprint!: SprintInfo;

  chartOptions: EChartsOption = {};
  isLoading: boolean = true;

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
    burnupData: {
      velocity: 0,
      totalJH: 0,
      totalStoryPoints: 0,
      points: []
    },
    sprintCommitInfo: {
      committedAtStart: [],
      addedDuring: [],
      removedDuring: []
    },
    sprintKpiInfo: {
      totalTickets: 0,
      committedAtStart: 0,
      committedAndDone: 0,
      addedDuring: 0,
      removedDuring: 0,
      doneTickets: 0,
      devDoneBeforeSprint: 0,
      engagementRespectePourcent: 0,
      ajoutsNonPrevusPourcent: 0,
      nonTerminesEngagesPourcent: 0,
      succesGlobalPourcent: 0,
      pointsCommited: 0,
      pointsAdded: 0,
      pointsRemoved: 0,
      typeCountCommitted: {},
      typeCountAdded: {},
      typeCountAll: {}
      
    }
  };


  private chartInstance: echarts.ECharts | null = null;
  private burnupService = inject(BurnupService);
  private sprintService = inject(SprintService);
  private themeService = inject(ThemeService);
  private themeSub?: Subscription;
  private currentPoints: { date: string; donePoints: number; capacity: number; velocity?: number }[] = [];
  private currentTotalStoryPoints = 0;

  constructor() {
    this.themeSub = this.themeService.activeTheme$.subscribe(() => {
      if (this.currentPoints.length > 0) {
        this.buildChartOptions(this.currentPoints, this.currentTotalStoryPoints);
      }
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['sprintId'] && this.sprintId) {
      this.loadBurnup(this.sprintId);
    }
    if (changes['sprint'] && this.sprint) {
      this.sprintInfo = {
        ...this.sprintInfo,
        ...this.sprint,
        burnupData: {
          ...this.sprintInfo.burnupData,
          ...this.sprint.burnupData // fusionne si jamais `sprint` contient aussi des points
        },
      };

  }
  }

  createSprint(): void {
    if (!this.sprintInfo.burnupData.points || this.sprintInfo.burnupData.points.length === 0) {
      alert('Les données burn-up ne sont pas encore chargées.');
      return;
    }
    const sprint: SprintInfo = { ...this.sprintInfo }; // ✅ Simplifié
    this.sprintService.createSprint(sprint).subscribe({
      next: (created) => {
        console.log('Sprint créé avec succès', created);
        alert(`Sprint créé avec ID: ${created.id}`);
      },
      error: (err) => {
        console.error('Erreur lors de la création du sprint', err);
        alert('Échec de la création du sprint');
      }
    });
  }

  ngOnDestroy(): void {
    this.themeSub?.unsubscribe();
  }

  loadBurnup(sprintId: string): void {
    this.isLoading = true;

    this.burnupService.getBurnupData(sprintId)
      .pipe(finalize(() => this.isLoading = false))
      .subscribe(data => {
        // ✅ Sécurise l'accès avec nullish coalescing
        this.sprintInfo.burnupData.velocity = data.velocity ?? 0;
        this.sprintInfo.burnupData.totalJH = data.totalJH ?? 0;
        this.sprintInfo.burnupData.points = data.points;
        this.sprintInfo.burnupData.totalStoryPoints = data.totalStoryPoints;


      this.currentPoints = data.points;
      this.currentTotalStoryPoints = data.totalStoryPoints;
      this.buildChartOptions(this.currentPoints, this.currentTotalStoryPoints);
      });
  }


  private buildChartOptions(points: { date: string; donePoints: number; capacity: number; velocity?: number }[], totalStoryPoints: number): void {
    const theme = getComputedStyle(document.documentElement);
    const axisColor = theme.getPropertyValue('--chart-axis').trim() || '#5c6f90';
    const gridColor = theme.getPropertyValue('--chart-grid').trim() || 'rgba(76, 103, 153, 0.2)';
    const storyColor = theme.getPropertyValue('--chart-story').trim() || '#3563ff';
    const capacityColor = theme.getPropertyValue('--chart-capacity').trim() || '#16a34a';
    const timeColor = theme.getPropertyValue('--chart-time').trim() || '#ff8a3d';

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const dates = points.map((p) => p.date);
    const done = points.map((p) => {
      const pointDate = new Date(p.date);
      pointDate.setHours(0, 0, 0, 0);
      return pointDate <= today ? p.donePoints : null;
    });
    const capacity = points.map((p) => p.capacity);
    const scope = points.map(() => totalStoryPoints);
    const velocities = points.map((p) => Number(p.velocity ?? 0));

    this.chartOptions = {
      color: [storyColor, capacityColor, timeColor],
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          const idx = params?.[0]?.dataIndex ?? 0;
          const date = dates[idx];
          const v = velocities[idx] ?? 0;
          const lines = (params as any[]).map((serie) => `${serie.marker} ${serie.seriesName}: <b>${serie.value ?? 0}</b>`).join('<br/>');
          return `<b>${date}</b><br/>${lines}<br/><span style="opacity:.8">Vélocité (pts/JH)</span>: <b>${v.toFixed(2)}</b>`;
        }
      },
      legend: { data: ['Réalisé', 'Capacité', 'Charge totale'], textStyle: { color: axisColor } },
      xAxis: { type: 'category', data: dates, axisLabel: { color: axisColor }, axisLine: { lineStyle: { color: axisColor } } },
      yAxis: { type: 'value', axisLabel: { color: axisColor }, splitLine: { lineStyle: { color: gridColor } } },
      series: [
        { name: 'Réalisé', type: 'line', data: done, smooth: true, symbol: 'circle', symbolSize: 6, lineStyle: { width: 3, color: storyColor } },
        { name: 'Capacité', type: 'line', data: capacity, smooth: true, symbol: 'none', lineStyle: { type: 'dotted', width: 2, color: capacityColor } },
        { name: 'Charge totale', type: 'line', data: scope, symbol: 'none', lineStyle: { type: 'dashed', width: 2, color: timeColor } }
      ]
    };
  }

  onChartInit(ec: echarts.ECharts): void {
    this.chartInstance = ec;
    this.chartInstance.resize();
  }
}
