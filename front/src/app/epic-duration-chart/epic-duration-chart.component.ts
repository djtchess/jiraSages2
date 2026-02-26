import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EChartsOption } from 'echarts';
import { NgxEchartsModule, NGX_ECHARTS_CONFIG } from 'ngx-echarts';

import { JiraService } from '../../service/jira.service';
import { EpicChildTicket, EpicDeliveryOverview } from '../../model/epic-duration.model';

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

  displayedColumns: string[] = ['epicKey', 'epicSummary', 'versions', 'storyPoints', 'days', 'developers', 'tickets'];
  chartOptions: EChartsOption = {};
  selectedEpic: EpicDeliveryOverview | null = null;
  ticketWorklogChartOptions: EChartsOption = {};

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
        this.selectedEpic = null;
        this.ticketWorklogChartOptions = {};
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

    if (this.selectedEpic && !this.filteredData.find(e => e.epicKey === this.selectedEpic?.epicKey)) {
      this.selectedEpic = null;
      this.ticketWorklogChartOptions = {};
    }
  }

  resetFilters(): void {
    this.selectedVersions = [];
    this.selectedSprints = [];
    this.applyFilters();
  }

  selectEpic(epic: EpicDeliveryOverview): void {
    this.selectedEpic = epic;
    this.buildTicketWorklogChart(epic.childTickets || []);
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
  <title>Epics · Vision autonome</title>
  <script src="https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js"></script>
  <style>
    :root {
      --bg: #060914;
      --panel: rgba(12, 19, 38, 0.82);
      --panel-soft: rgba(16, 26, 51, 0.68);
      --text: #e9f1ff;
      --muted: #9eb3d6;
      --line: rgba(112, 149, 255, 0.24);
      --primary: #67b7ff;
      --secondary: #b388ff;
      --accent: #38ffd3;
      --danger: #ff7cb8;
      --shadow: 0 18px 40px rgba(0, 0, 0, 0.45);
      --radius: 16px;
    }

    * { box-sizing: border-box; }

    body {
      margin: 0;
      padding: 28px;
      color: var(--text);
      background:
        radial-gradient(1000px 500px at 8% -8%, rgba(56, 255, 211, 0.16), transparent 60%),
        radial-gradient(900px 520px at 100% 0%, rgba(179, 136, 255, 0.2), transparent 60%),
        linear-gradient(145deg, #060914, #0c1326 40%, #0a1022 100%);
      font-family: Inter, Segoe UI, Roboto, Helvetica, Arial, sans-serif;
      min-height: 100vh;
    }

    .hero {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      margin-bottom: 20px;
    }

    .title {
      margin: 0;
      font-size: 30px;
      font-weight: 800;
      letter-spacing: 0.3px;
      background: linear-gradient(90deg, #a3d2ff, #d6bcff 45%, #8cffea);
      -webkit-background-clip: text;
      background-clip: text;
      color: transparent;
    }

    .subtitle {
      margin-top: 8px;
      color: var(--muted);
      font-size: 14px;
    }

    .pill {
      display: inline-flex;
      align-items: center;
      padding: 8px 12px;
      border: 1px solid var(--line);
      border-radius: 999px;
      background: rgba(19, 30, 57, 0.7);
      color: var(--muted);
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
    }

    .kpis {
      display: grid;
      grid-template-columns: repeat(4, minmax(180px, 1fr));
      gap: 12px;
      margin-bottom: 18px;
    }

    .kpi {
      background: linear-gradient(160deg, rgba(17, 27, 51, 0.9), rgba(10, 18, 37, 0.85));
      border: 1px solid var(--line);
      border-radius: var(--radius);
      box-shadow: var(--shadow);
      padding: 14px 16px;
    }

    .kpi-label {
      color: var(--muted);
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      margin-bottom: 8px;
    }

    .kpi-value {
      font-size: 26px;
      font-weight: 800;
      color: #f4f8ff;
    }

    .panel {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: var(--radius);
      box-shadow: var(--shadow);
      backdrop-filter: blur(8px);
      padding: 16px;
      margin-bottom: 14px;
    }

    .filters {
      display: grid;
      grid-template-columns: 1fr 1fr auto;
      gap: 12px;
      align-items: end;
    }

    label {
      display: block;
      margin-bottom: 8px;
      color: var(--muted);
      font-size: 12px;
      letter-spacing: 0.5px;
      text-transform: uppercase;
      font-weight: 700;
    }

    select {
      width: 100%;
      min-height: 112px;
      border-radius: 12px;
      border: 1px solid var(--line);
      background: var(--panel-soft);
      color: var(--text);
      padding: 8px;
      outline: none;
    }

    option { padding: 4px 8px; }

    button {
      border: 1px solid rgba(127, 193, 255, 0.4);
      background: linear-gradient(135deg, rgba(103, 183, 255, 0.2), rgba(179, 136, 255, 0.25));
      color: #ebf4ff;
      border-radius: 12px;
      padding: 10px 16px;
      font-weight: 700;
      letter-spacing: 0.3px;
      cursor: pointer;
      transition: transform .15s ease, box-shadow .15s ease;
    }

    button:hover {
      transform: translateY(-1px);
      box-shadow: 0 10px 24px rgba(103, 183, 255, 0.22);
    }

    #chart, #ticketChart {
      width: 100%;
      height: 450px;
      border-radius: 14px;
      border: 1px solid var(--line);
      background: rgba(9, 16, 32, 0.75);
    }

    .table-wrap {
      overflow: auto;
      border-radius: 12px;
      border: 1px solid var(--line);
    }

    table {
      width: 100%;
      border-collapse: collapse;
      min-width: 920px;
      background: rgba(8, 14, 30, 0.85);
    }

    th, td {
      padding: 10px 12px;
      border-bottom: 1px solid rgba(103, 146, 255, 0.14);
      text-align: left;
      vertical-align: top;
      font-size: 13px;
    }

    th {
      position: sticky;
      top: 0;
      background: rgba(18, 29, 57, 0.96);
      color: #bed2f5;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      font-size: 11px;
    }

    td { color: #e7efff; }

    tr:hover td { background: rgba(73, 120, 255, 0.08); }

    a { color: var(--accent); text-decoration: none; }
    a:hover { text-decoration: underline; }

    .section-title {
      margin: 0 0 10px 0;
      font-size: 18px;
      color: #dcebff;
    }

    @media (max-width: 1000px) {
      body { padding: 18px; }
      .hero { flex-direction: column; }
      .kpis { grid-template-columns: repeat(2, minmax(160px, 1fr)); }
      .filters { grid-template-columns: 1fr; }
    }
  </style>
</head>
<body>
  <header class="hero">
    <div>
      <h1 class="title">Epic Delivery Radar</h1>
      <div class="subtitle">Projet ${this.projectKey} · Version autonome exportée</div>
    </div>
    <div class="pill" id="generatedAt">Génération: —</div>
  </header>

  <section class="kpis">
    <article class="kpi"><div class="kpi-label">Epics visibles</div><div class="kpi-value" id="kpiEpics">0</div></article>
    <article class="kpi"><div class="kpi-label">Versions disponibles</div><div class="kpi-value" id="kpiVersions">0</div></article>
    <article class="kpi"><div class="kpi-label">Sprints disponibles</div><div class="kpi-value" id="kpiSprints">0</div></article>
    <article class="kpi"><div class="kpi-label">Tickets enfants visibles</div><div class="kpi-value" id="kpiTickets">0</div></article>
  </section>

  <section class="panel filters">
    <div>
      <label for="versionFilter">Filtrer par versions</label>
      <select id="versionFilter" multiple></select>
    </div>
    <div>
      <label for="sprintFilter">Filtrer par sprints</label>
      <select id="sprintFilter" multiple></select>
    </div>
    <div>
      <button id="resetBtn">Réinitialiser les filtres</button>
    </div>
  </section>

  <section class="panel">
    <h2 class="section-title">Vue macro des epics</h2>
    <div id="chart"></div>
  </section>

  <section class="panel">
    <h2 class="section-title">Synthèse des epics</h2>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Epic</th>
            <th>Résumé</th>
            <th>Versions</th>
            <th>Story Points</th>
            <th>Temps (jours)</th>
            <th>Vélocité</th>
            <th>Développeurs</th>
          </tr>
        </thead>
        <tbody id="rows"></tbody>
      </table>
    </div>
  </section>

  <section class="panel">
    <h2 class="section-title" id="selectedEpicTitle">Détails tickets enfants</h2>
    <div id="ticketChart"></div>
    <div class="table-wrap" style="margin-top:12px;">
      <table>
        <thead>
          <tr>
            <th>Ticket</th>
            <th>Résumé</th>
            <th>Statut</th>
            <th>Story Points</th>
            <th>Temps (jours)</th>
            <th>Développeurs</th>
          </tr>
        </thead>
        <tbody id="ticketRows"></tbody>
      </table>
    </div>
  </section>

  <script>
    const payload = ${JSON.stringify(payload)};
    const data = payload.rows || [];
    const generatedDate = new Date(payload.generatedAt);

    const allVersions = [...new Set(data.flatMap(r => r.versionNames || []))].sort((a, b) => a.localeCompare(b));
    const allSprints = [...new Set(data.flatMap(r => (r.sprintDeliveries || []).map(s => s.sprintName || '')))].filter(Boolean).sort((a, b) => a.localeCompare(b));

    const versionFilter = document.getElementById('versionFilter');
    const sprintFilter = document.getElementById('sprintFilter');
    const generatedAtEl = document.getElementById('generatedAt');
    generatedAtEl.textContent = 'Génération: ' + (isNaN(generatedDate.getTime()) ? 'inconnue' : generatedDate.toLocaleString('fr-FR'));

    let selectedVersions = [];
    let selectedSprints = [];
    let selectedEpicIndex = 0;

    const escapeHtml = (value = '') => String(value)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');

    allVersions.forEach(v => {
      const option = document.createElement('option');
      option.value = v;
      option.textContent = v;
      versionFilter.appendChild(option);
    });

    allSprints.forEach(s => {
      const option = document.createElement('option');
      option.value = s;
      option.textContent = s;
      sprintFilter.appendChild(option);
    });

    const chart = echarts.init(document.getElementById('chart'));
    const ticketChart = echarts.init(document.getElementById('ticketChart'));

    function filteredRows() {
      return data.filter(row => {
        const versionOk = selectedVersions.length === 0 || (row.versionNames || []).some(v => selectedVersions.includes(v));
        const sprintNames = (row.sprintDeliveries || []).map(s => s.sprintName);
        const sprintOk = selectedSprints.length === 0 || sprintNames.some(s => selectedSprints.includes(s));
        return versionOk && sprintOk;
      });
    }

    function renderTicketArea(rows) {
      const epic = rows[selectedEpicIndex] || rows[0] || null;
      const tickets = epic?.childTickets || [];

      document.getElementById('selectedEpicTitle').textContent = epic
        ? 'Détails tickets enfants · ' + epic.epicKey
        : 'Détails tickets enfants';

      document.getElementById('kpiTickets').textContent = String(tickets.length);

      const ticketRows = document.getElementById('ticketRows');
      ticketRows.innerHTML = '';

      tickets.forEach(ticket => {
        const tr = document.createElement('tr');
        tr.innerHTML = ''
          + '<td><a href="' + escapeHtml(ticket.ticketUrl || '#') + '" target="_blank" rel="noopener">' + escapeHtml(ticket.ticketKey || '') + '</a></td>'
          + '<td>' + escapeHtml(ticket.summary || '') + '</td>'
          + '<td>' + escapeHtml(ticket.status || 'Inconnu') + '</td>'
          + '<td>' + Number(ticket.storyPoints || 0).toFixed(2) + '</td>'
          + '<td>' + Number(ticket.timeSpentDays || 0).toFixed(2) + '</td>'
          + '<td>' + escapeHtml((ticket.developers || []).join(', ')) + '</td>';
        ticketRows.appendChild(tr);
      });

      ticketChart.setOption({
        backgroundColor: 'transparent',
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: 40, right: 20, top: 30, bottom: 60 },
        xAxis: {
          type: 'category',
          data: tickets.map(t => t.ticketKey),
          axisLabel: { rotate: 28, color: '#bdd2ff' },
          axisLine: { lineStyle: { color: 'rgba(111, 153, 255, 0.4)' } }
        },
        yAxis: {
          type: 'value',
          name: 'Jours',
          nameTextStyle: { color: '#bdd2ff' },
          axisLabel: { color: '#bdd2ff' },
          splitLine: { lineStyle: { color: 'rgba(111, 153, 255, 0.15)' } }
        },
        series: [{
          type: 'bar',
          data: tickets.map(t => Number(t.timeSpentDays || 0)),
          barMaxWidth: 34,
          itemStyle: { color: '#7fd0ff', shadowBlur: 10, shadowColor: 'rgba(127, 208, 255, 0.35)' }
        }]
      });
    }

    function render() {
      const rows = filteredRows();
      document.getElementById('kpiEpics').textContent = String(rows.length);
      document.getElementById('kpiVersions').textContent = String(allVersions.length);
      document.getElementById('kpiSprints').textContent = String(allSprints.length);

      if (selectedEpicIndex >= rows.length) {
        selectedEpicIndex = 0;
      }

      const tbody = document.getElementById('rows');
      tbody.innerHTML = '';

      rows.forEach((row, idx) => {
        const velocity = Number(row.epicVelocity || 0).toFixed(2);
        const tr = document.createElement('tr');
        tr.innerHTML = ''
          + '<td><a href="#" data-idx="' + idx + '">' + escapeHtml(row.epicKey || '') + '</a></td>'
          + '<td>' + escapeHtml(row.epicSummary || '') + '</td>'
          + '<td>' + escapeHtml((row.versionNames || []).join(', ') || 'Sans version') + '</td>'
          + '<td>' + Number(row.totalStoryPoints || 0).toFixed(2) + '</td>'
          + '<td>' + Number(row.totalTimeSpentDays || 0).toFixed(2) + '</td>'
          + '<td>' + velocity + '</td>'
          + '<td>' + escapeHtml((row.developers || []).join(', ')) + '</td>';
        tbody.appendChild(tr);
      });

      tbody.querySelectorAll('a[data-idx]').forEach(a => {
        a.addEventListener('click', event => {
          event.preventDefault();
          selectedEpicIndex = Number(a.getAttribute('data-idx'));
          renderTicketArea(rows);
        });
      });

      chart.setOption({
        backgroundColor: 'transparent',
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        legend: {
          data: ['Story Points', 'Temps (jours)'],
          top: 0,
          textStyle: { color: '#d7e7ff' }
        },
        grid: { left: 40, right: 20, top: 46, bottom: 64 },
        xAxis: {
          type: 'category',
          data: rows.map(r => r.epicKey),
          axisLabel: { rotate: 28, color: '#c3d8ff' },
          axisLine: { lineStyle: { color: 'rgba(111, 153, 255, 0.4)' } }
        },
        yAxis: {
          type: 'value',
          axisLabel: { color: '#c3d8ff' },
          splitLine: { lineStyle: { color: 'rgba(111, 153, 255, 0.15)' } }
        },
        series: [
          {
            name: 'Story Points',
            type: 'bar',
            barMaxWidth: 34,
            data: rows.map(r => Number(r.totalStoryPoints || 0)),
            itemStyle: { color: '#74bbff', borderRadius: [6, 6, 0, 0] }
          },
          {
            name: 'Temps (jours)',
            type: 'bar',
            barMaxWidth: 34,
            data: rows.map(r => Number(r.totalTimeSpentDays || 0)),
            itemStyle: { color: '#c792ff', borderRadius: [6, 6, 0, 0] }
          }
        ]
      });

      renderTicketArea(rows);
    }

    versionFilter.addEventListener('change', () => {
      selectedVersions = Array.from(versionFilter.selectedOptions).map(option => option.value);
      selectedEpicIndex = 0;
      render();
    });

    sprintFilter.addEventListener('change', () => {
      selectedSprints = Array.from(sprintFilter.selectedOptions).map(option => option.value);
      selectedEpicIndex = 0;
      render();
    });

    document.getElementById('resetBtn').addEventListener('click', () => {
      selectedVersions = [];
      selectedSprints = [];
      Array.from(versionFilter.options).forEach(option => option.selected = false);
      Array.from(sprintFilter.options).forEach(option => option.selected = false);
      selectedEpicIndex = 0;
      render();
    });

    window.addEventListener('resize', () => {
      chart.resize();
      ticketChart.resize();
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
  formatDevelopers(row: EpicDeliveryOverview): string {
    return (row.developers || []).join(', ');
  }
  private buildTicketWorklogChart(tickets: EpicChildTicket[]): void {
    this.ticketWorklogChartOptions = {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: { type: 'category', data: tickets.map(t => t.ticketKey), axisLabel: { rotate: 35 } },
      yAxis: { type: 'value', name: 'Jours passés' },
      series: [
        {
          name: 'Temps passé',
          type: 'bar',
          data: tickets.map(t => Number(t.timeSpentDays || 0)),
          itemStyle: { color: '#ef6c00' }
        }
      ]
    };
  }

  private buildChart(rows: EpicDeliveryOverview[]): void {
    const epics = rows.map(r => r.epicKey);
    const storyPoints = rows.map(r => Number(r.totalStoryPoints || 0));
    const timeSpentDays = rows.map(r => Number(r.totalTimeSpentDays || 0));

    this.chartOptions = {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: any) => {
          const idx = params?.[0]?.dataIndex ?? 0;
          const epic = rows[idx];
          const versions = epic?.versionNames?.join(', ') ?? 'Sans version';
          const sprints = epic?.sprintDeliveries?.map(s => s.sprintName).join(' / ') ?? '-';
          const devs = epic?.developers?.join(', ') ?? '-';
          const lines = (params as any[]).map(p => `${p.marker} ${p.seriesName}: <b>${p.value}</b>`).join('<br/>');
          return `<b>${epic?.epicKey ?? ''}</b><br/>${lines}<br/>Versions tickets enfants: <b>${versions}</b><br/>Sprints: <b>${sprints}</b><br/>Développeurs: <b>${devs}</b>`;
        }
      },
      legend: { data: ['Story Points', 'Temps (jours)'] },
      grid: { left: '3%', right: '4%', bottom: '8%', containLabel: true },
      xAxis: { type: 'category', data: epics, axisLabel: { rotate: 35 } },
      yAxis: { type: 'value', name: 'Valeur' },
      series: [
        { name: 'Story Points', type: 'bar', data: storyPoints, itemStyle: { color: '#1976d2' } },
        { name: 'Temps (jours)', type: 'bar', data: timeSpentDays, itemStyle: { color: '#ef6c00' } }
      ]
    };
  }
}
