import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EChartsOption } from 'echarts';
import { NgxEchartsModule, NGX_ECHARTS_CONFIG } from 'ngx-echarts';

import { JiraService } from '../../service/jira.service';
import { EpicDeliveryOverview } from '../../model/epic-duration.model';

@Component({
  selector: 'app-epic-duration-chart',
  standalone: true,
  imports: [CommonModule, FormsModule, NgxEchartsModule, MatTableModule, MatProgressSpinnerModule],
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
  filteredData: EpicDeliveryOverview[] = [];
  allVersions: string[] = [];
  allSprints: string[] = [];
  selectedVersions: string[] = [];
  selectedSprints: string[] = [];

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
        this.allVersions = Array.from(new Set(rows.flatMap(r => r.versionNames))).sort((a, b) => a.localeCompare(b));
        this.allSprints = Array.from(new Set(rows.flatMap(r => r.sprintDeliveries.map(s => s.sprintName)))).sort((a, b) => a.localeCompare(b));
        this.applyFilters();
        this.isLoading = false;
      },
      error: () => {
        this.data = [];
        this.filteredData = [];
        this.chartOptions = {};
        this.isLoading = false;
      }
    });
  }

  applyFilters(): void {
    this.filteredData = this.data.filter(row => {
      const versionOk = this.selectedVersions.length === 0 || row.versionNames.some(v => this.selectedVersions.includes(v));
      const sprintNames = row.sprintDeliveries.map(s => s.sprintName);
      const sprintOk = this.selectedSprints.length === 0 || sprintNames.some(s => this.selectedSprints.includes(s));
      return versionOk && sprintOk;
    });
    this.buildChart(this.filteredData);
  }

  resetFilters(): void {
    this.selectedVersions = [];
    this.selectedSprints = [];
    this.applyFilters();
  }

  exportStandaloneHtml(): void {
    const payload = {
      projectKey: this.projectKey,
      generatedAt: new Date().toISOString(),
      rows: this.data
    };

    const html = `<!doctype html>
<html lang="fr">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Epics - Sprints & Versions</title>
  <script src="https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js"></script>
  <style>
    body { font-family: Arial, sans-serif; margin: 16px; background:#f6f8ff; }
    .kpis { display:grid; grid-template-columns:repeat(3,minmax(160px,1fr)); gap:12px; margin-bottom:12px; }
    .card { background:#fff; border:1px solid #dce3ff; border-radius:10px; padding:12px; }
    .label { color:#5f6b85; font-size:12px; }
    .value { font-weight:700; font-size:20px; color:#1f2f56; }
    .filters { display:grid; grid-template-columns:1fr 1fr auto; gap:12px; background:#fff; border:1px solid #dce3ff; border-radius:10px; padding:12px; margin-bottom:12px; }
    select { min-height:90px; width:100%; }
    button { height:40px; border:none; background:#3f51b5; color:#fff; border-radius:8px; padding:0 12px; cursor:pointer; }
    #chart { width:100%; height:480px; background:#fff; border-radius:10px; border:1px solid #dce3ff; margin-bottom:12px; }
    table { width:100%; border-collapse: collapse; background:#fff; border:1px solid #dce3ff; }
    th, td { border-bottom:1px solid #edf0fb; padding:8px; text-align:left; }
    th { background:#f2f5ff; }
  </style>
</head>
<body>
  <h2>Epics livrés - Projet ${this.projectKey}</h2>
  <div style="color:#6b7899; margin-bottom:10px;">Export généré le ${new Date().toLocaleString('fr-FR')}</div>
  <div class="kpis">
    <div class="card"><div class="label">Epics affichés</div><div class="value" id="kpiEpics">0</div></div>
    <div class="card"><div class="label">Versions disponibles</div><div class="value" id="kpiVersions">0</div></div>
    <div class="card"><div class="label">Sprints disponibles</div><div class="value" id="kpiSprints">0</div></div>
  </div>
  <div class="filters">
    <div><label>Filtrer par versions</label><select id="versionFilter" multiple></select></div>
    <div><label>Filtrer par sprints</label><select id="sprintFilter" multiple></select></div>
    <div style="display:flex;align-items:end;"><button id="resetBtn">Réinitialiser</button></div>
  </div>
  <div id="chart"></div>
  <table>
    <thead><tr><th>Epic</th><th>Résumé</th><th>Statut</th><th>Versions (tickets enfants)</th><th>Sprints</th></tr></thead>
    <tbody id="rows"></tbody>
  </table>

  <script>
    const raw = ${JSON.stringify(payload)};
    let selectedVersions = [];
    let selectedSprints = [];
    const data = raw.rows || [];

    const allVersions = [...new Set(data.flatMap(r => r.versionNames || []))].sort();
    const allSprints = [...new Set(data.flatMap(r => (r.sprintDeliveries || []).map(s => s.sprintName)))].sort();

    const versionFilter = document.getElementById('versionFilter');
    const sprintFilter = document.getElementById('sprintFilter');
    allVersions.forEach(v => { const o=document.createElement('option'); o.value=v; o.textContent=v; versionFilter.appendChild(o); });
    allSprints.forEach(s => { const o=document.createElement('option'); o.value=s; o.textContent=s; sprintFilter.appendChild(o); });

    const chart = echarts.init(document.getElementById('chart'));

    function getFiltered() {
      return data.filter(r => {
        const versionOk = selectedVersions.length === 0 || (r.versionNames || []).some(v => selectedVersions.includes(v));
        const sprints = (r.sprintDeliveries || []).map(s => s.sprintName);
        const sprintOk = selectedSprints.length === 0 || sprints.some(s => selectedSprints.includes(s));
        return versionOk && sprintOk;
      });
    }

    function render() {
      const rows = getFiltered();
      document.getElementById('kpiEpics').textContent = String(rows.length);
      document.getElementById('kpiVersions').textContent = String(allVersions.length);
      document.getElementById('kpiSprints').textContent = String(allSprints.length);

      const tbody = document.getElementById('rows');
      tbody.innerHTML = '';
      rows.forEach(r => {
        const tr = document.createElement('tr');
        tr.innerHTML = '<td>'+r.epicKey+'</td><td>'+r.epicSummary+'</td><td>'+r.status+'</td><td>'+(r.versionNames||[]).join(', ')+'</td><td>'+(r.sprintDeliveries||[]).map(s=>s.sprintName).join(' / ')+'</td>';
        tbody.appendChild(tr);
      });

      chart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        legend: { data: ['Nb sprints', 'Nb versions'] },
        xAxis: { type: 'category', data: rows.map(r => r.epicKey), axisLabel: { rotate: 35 } },
        yAxis: { type: 'value' },
        series: [
          { name: 'Nb sprints', type: 'bar', data: rows.map(r => (r.sprintDeliveries||[]).length) },
          { name: 'Nb versions', type: 'bar', data: rows.map(r => (r.versionNames||[]).length) }
        ]
      });
    }

    versionFilter.addEventListener('change', () => { selectedVersions = Array.from(versionFilter.selectedOptions).map(o => o.value); render(); });
    sprintFilter.addEventListener('change', () => { selectedSprints = Array.from(sprintFilter.selectedOptions).map(o => o.value); render(); });
    document.getElementById('resetBtn').addEventListener('click', () => {
      selectedVersions = []; selectedSprints = [];
      Array.from(versionFilter.options).forEach(o => o.selected = false);
      Array.from(sprintFilter.options).forEach(o => o.selected = false);
      render();
    });

    render();
  </script>
</body>
</html>`;

    const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `epic-delivery-${this.projectKey}.html`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
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
          return `<b>${epic?.epicKey ?? ''}</b><br/>${lines}<br/>Versions tickets enfants: <b>${versions}</b><br/>Sprints: <b>${sprints}</b>`;
        }
      },
      legend: { data: ['Nb sprints', 'Nb versions'] },
      grid: { left: '3%', right: '4%', bottom: '8%', containLabel: true },
      xAxis: { type: 'category', data: epics, axisLabel: { rotate: 35 } },
      yAxis: { type: 'value', name: 'Nombre' },
      series: [
        { name: 'Nb sprints', type: 'bar', data: sprintCount, itemStyle: { color: '#1976d2' } },
        { name: 'Nb versions', type: 'bar', data: versionCount, itemStyle: { color: '#7b1fa2' } }
      ]
    };
  }
}
