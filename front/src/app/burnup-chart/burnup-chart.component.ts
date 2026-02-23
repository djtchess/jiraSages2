import {
  Component,
  Input,
  OnChanges,
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
export class BurnupChartComponent implements OnChanges {
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

  loadBurnup(sprintId: string): void {
    this.isLoading = true;

    this.burnupService.getBurnupData(sprintId)
      .pipe(finalize(() => this.isLoading = false))
      .subscribe(data => {
        const today = new Date();
        today.setHours(0, 0, 0, 0);

        // ✅ Sécurise l'accès avec nullish coalescing
        this.sprintInfo.burnupData.velocity = data.velocity ?? 0;
        this.sprintInfo.burnupData.totalJH = data.totalJH ?? 0;
        this.sprintInfo.burnupData.points = data.points;
        this.sprintInfo.burnupData.totalStoryPoints = data.totalStoryPoints;


      const dates = data.points.map(p => p.date);
      const done = data.points.map(p => {
        const pointDate = new Date(p.date);
        pointDate.setHours(0, 0, 0, 0);
        return pointDate <= today ? p.donePoints : null; // null pour éviter les lignes continues
      });
      const capacity = data.points.map(p => p.capacity);
      const scope = data.points.map(() => data.totalStoryPoints);
      const velocities = data.points.map(p => Number(p.velocity ?? 0));

        this.chartOptions = {
          tooltip: {
            trigger: 'axis',
            formatter: (params: any) => {
              // params = tableau des points (une entrée par série au même x)
              const idx   = params?.[0]?.dataIndex ?? 0;
              const date  = dates[idx];
              const v     = velocities[idx] ?? 0;

              // Construit les lignes des séries présentes au tooltip
              const lines = (params as any[]).map(s =>
                `${s.marker} ${s.seriesName}: <b>${s.value ?? 0}</b>`
              ).join('<br/>');

              // Ajoute la vélocité uniquement dans le tooltip
              const velocityLine = `<span style="opacity:.8">Vélocité (pts/JH)</span>: <b>${v.toFixed(2)}</b>`;

              return `<b>${date}</b><br/>${lines}<br/>${velocityLine}`;
            }
          },
          legend: { data: ['Réalisé', 'Capacité', 'Charge totale'] },
          xAxis: { type: 'category', data: dates },
          yAxis: { type: 'value' },
          series: [
            {
              name: 'Réalisé',
              type: 'line',
              data: done,
              smooth: true,
              symbol: 'circle',
              symbolSize: 6,
              lineStyle: { width: 3 }
            },
            {
              name: 'Capacité',
              type: 'line',
              data: capacity,
              smooth: true,
              symbol: 'none',
              lineStyle: { type: 'dotted', width: 2 }
            },
            {
              name: 'Charge totale',
              type: 'line',
              data: scope,
              symbol: 'none',
              lineStyle: { type: 'dashed', color: '#888', width: 2 }
            }
          ]
        };
      });
  }

  onChartInit(ec: echarts.ECharts): void {
    this.chartInstance = ec;
    this.chartInstance.resize();
  }
}
