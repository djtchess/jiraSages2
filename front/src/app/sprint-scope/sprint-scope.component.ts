import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import {  SprintInfo, Ticket } from '../../model/SprintInfo.model';
import { JiraService } from '../../service/jira.service';
import { TicketTableComponent } from "../ticket-table/ticket-table.component";
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { EChartsOption, PieSeriesOption } from 'echarts';
import { NgxEchartsModule, NGX_ECHARTS_CONFIG } from 'ngx-echarts';
import * as echarts from 'echarts'; // ✅ Import pour typage strict
import { Subscription } from 'rxjs';
import { ThemeService } from '../theme.service';

type TypeCountMap = Record<string, { nbTickets: number; nbStoryPoints: number }>;

@Component({
  selector: 'app-sprint-scope',
  standalone: true,
  imports: [CommonModule , TicketTableComponent, MatIconModule, MatCardModule, NgxEchartsModule ],
  providers: [
    {
      provide: NGX_ECHARTS_CONFIG,
      useFactory: () => ({ echarts: () => import('echarts') }),
    },
  ],
  templateUrl: './sprint-scope.component.html',
  styleUrl: './sprint-scope.component.css'
})
export class SprintScopeComponent implements OnInit, OnDestroy {
  @Input() sprintId!: string;
  sprintInfo?: SprintInfo;

  kpiChartOptions?: EChartsOption;
  ticketTypeChartOptions?: EChartsOption;
  ticketNbTypeChartOptions?: EChartsOption;

  private charts: Record<string, echarts.ECharts> = {};
  showCommittedByType = true;
  showAddedByType = true;
  showTotalByType = true;

  // === Marges et dimensions slide (LAYOUT_16x9: 10" x 5.625") ===
  private readonly PPT = {
    W: 10,
    H: 5.625,
    M: { l: 0.6, r: 0.6, t: 0.6, b: 0.6 }  // marges souhaitées
  } as const;

  // Petite aide pour la largeur utile
  private get contentWidth(): number {
    return this.PPT.W - this.PPT.M.l - this.PPT.M.r; // 10 - 0.6 - 0.6 = 8.8"
  }


  private themeSub?: Subscription;

  constructor(
    private jiraService: JiraService,
    private themeService: ThemeService,
  ) {}

  ngOnInit(): void {
    this.jiraService.getSprintFullInfo(this.sprintId).subscribe(data => {
      console.log(data);
      this.sprintInfo = data;
      this.updateKpiChart();
      this.updateTicketTypeChart();
    });

    this.themeSub = this.themeService.activeTheme$.subscribe(() => {
      this.updateKpiChart();
      this.updateTicketTypeChart();
    });
  }

  ngOnDestroy(): void {
    this.themeSub?.unsubscribe();
  }

  onChartInit(id: 'kpi' | 'typeSp' | 'typeNb', ec: echarts.ECharts): void {
    this.charts[id] = ec;
    setTimeout(() => ec.resize(), 0);
  }

  getKpiClass(percent: number): string {
    console.log('KPI Percent:', percent);
    if (percent > 100) return 'kpi-bad';
    if (percent >= 90) return 'kpi-good';
    if (percent >= 60) return 'kpi-medium';
    return 'kpi-bad';
  }

  updateKpiChart(): void {
    if (!this.sprintInfo?.sprintKpiInfo) return;

    const chartTheme = this.getChartTheme();

    this.kpiChartOptions = {
      title: {
        text: 'Indicateurs principaux',
        left: 'center',
        textStyle: { color: chartTheme.titleColor, fontWeight: 700 }
      },
      tooltip: {
        trigger: 'item',
        textStyle: { color: chartTheme.labelColor },
        formatter: (params: any) => `${params.name}: ${params.value.toFixed(1)}%`
      },
      legend: {
        bottom: '0',
        left: 'center',
        textStyle: { color: chartTheme.legendColor }
      },
      series: [
        {
          name: 'KPIs',
          type: 'pie',
          radius: '50%',
          color: ['#4caf50', '#ff9800', '#f44336'],
          data: [
            { value: this.sprintInfo?.sprintKpiInfo.engagementRespectePourcent, name: 'Engagement respecté' },
            { value: this.sprintInfo?.sprintKpiInfo.ajoutsNonPrevusPourcent, name: 'Ajouts en cours' },
            { value: this.sprintInfo?.sprintKpiInfo.nonTerminesEngagesPourcent, name: 'Engagés non terminés' }
          ],
          label: {
            formatter: '{b}\n{d}%',
            fontSize: 13,
            fontWeight: 700,
            color: chartTheme.labelColor,
            textBorderColor: chartTheme.labelHaloColor,
            textBorderWidth: 3
          },
          labelLine: {
            lineStyle: { color: chartTheme.labelLineColor, width: 1.5 }
          },
          emphasis: {
            label: { color: chartTheme.labelColor, textBorderColor: chartTheme.labelHaloColor, textBorderWidth: 4 },
            itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.3)' }
          }
        }
      ]
    };
  }

  /** Palette fixe : ajoute ou modifie les couples type/couleur à ta convenance */
  TICKET_TYPE_COLORS: Record<string, string> = {
    "Analyse technique":   '#42a5f5',
    "Bug":     '#0fbd57ff',
    "Document":    '#380770ff',
    "Story":   '#d81b44ff',
    "Tâche DevOps":   '#a9cce0ff',
    "Tâche Enovacom":   '#b8e933ff',
    "Tâche Technique":   '#14a7ebff',
    UNKNOWN: '#bdbdbd'
  };

  /** Fallback : génère une couleur stable si le type n’est pas dans la palette */
  stringToColor(str: string): string {
    let hash = 0;
    for (let i = 0; i < str.length; i++) hash = str.charCodeAt(i) + ((hash << 5) - hash);
    return `#${('000000' + (hash & 0xffffff).toString(16)).slice(-6)}`;
  }

  updateTicketTypeChart(): void {
    const typeCount = this.sprintInfo?.sprintKpiInfo?.typeCountAll;
    if (!typeCount) { return; }

    /* ---------- Données ---------- */
    const dataSP: PieSeriesOption['data'] = Object.entries(typeCount).map(([type, c]) => ({
      name: type,
      value: c.nbStoryPoints,
      itemStyle: { color: this.TICKET_TYPE_COLORS[type] ?? this.stringToColor(type) }
    }));

    const dataNB: PieSeriesOption['data'] = Object.entries(typeCount).map(([type, c]) => ({
      name: type,
      value: c.nbTickets,
      itemStyle: { color: this.TICKET_TYPE_COLORS[type] ?? this.stringToColor(type) }
    }));

    const chartTheme = this.getChartTheme();

    /* ---------- Paramètres communs ---------- */
    const basePie: Partial<PieSeriesOption> = {
      type: 'pie',
      radius: ['40%', '65%'],
      avoidLabelOverlap: true,
      itemStyle: {
        borderRadius: 6,
        borderColor: chartTheme.sliceBorderColor,
        borderWidth: 2
      },
      label: {
        formatter: '{b}\n{d}%',
        fontSize: 13,
        fontWeight: 700,
        color: chartTheme.labelColor,
        textBorderColor: chartTheme.labelHaloColor,
        textBorderWidth: 3
      },
      labelLine: {
        lineStyle: { color: chartTheme.labelLineColor, width: 1.5 }
      },
      emphasis: {
        label: { color: chartTheme.labelColor, textBorderColor: chartTheme.labelHaloColor, textBorderWidth: 4 }
      }
    };

    /* ---------- Story-points par type ---------- */
    this.ticketTypeChartOptions = <EChartsOption>{
      title: { text: 'Répartition Story-Points par type de ticket', left: 'center', textStyle: { color: chartTheme.titleColor, fontWeight: 700 } },
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)', textStyle: { color: chartTheme.labelColor } },
      legend: { bottom: 0, left: 'center', textStyle: { color: chartTheme.legendColor } },
      series: [{ ...basePie, name: 'Types', data: dataSP }]
    };

    /* ---------- Nombre de tickets par type ---------- */
    this.ticketNbTypeChartOptions = <EChartsOption>{
      title: { text: 'Répartition Nombre de tickets par type', left: 'center', textStyle: { color: chartTheme.titleColor, fontWeight: 700 } },
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)', textStyle: { color: chartTheme.labelColor } },
      legend: { bottom: 0, left: 'center', textStyle: { color: chartTheme.legendColor } },
      series: [{ ...basePie, name: 'Types', data: dataNB }]
    };
  }


  private getChartTheme(): {
    titleColor: string;
    legendColor: string;
    labelColor: string;
    labelLineColor: string;
    labelHaloColor: string;
    sliceBorderColor: string;
  } {
    const styles = getComputedStyle(document.documentElement);
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark';

    return {
      titleColor: styles.getPropertyValue('--color-text').trim() || (isDark ? '#f3f6ff' : '#1a2238'),
      legendColor: styles.getPropertyValue('--color-text-muted').trim() || (isDark ? '#d3def8' : '#47526c'),
      labelColor: isDark ? '#f8fbff' : '#1f2a3f',
      labelLineColor: isDark ? '#dce8ff' : '#667899',
      labelHaloColor: isDark ? '#091327' : '#ffffff',
      sliceBorderColor: isDark ? '#d9e7ff' : '#ffffff',
    };
  }

  /** Même logique que TicketTableComponent pour le statut "Terminé" */
  private isTicketDoneForExport(ticket: Ticket): boolean {
    const doneStatuses = [
      'TACHE TECHNIQUE TESTEE', 'DEV TERMINE', 'FAIT',
      'LIVRÉ À TESTER', 'NON TESTABLE', 'RESOLU',
      'TESTÉ', 'TESTS UTR', 'TERMINE', 'INTEGRATION PR', 'READY TO DEMO'
    ];
    return ticket.status != null && doneStatuses.includes(ticket.status.toUpperCase());
  }

  /** Petite aide pour formatter un pourcentage en évitant les erreurs si undefined */
  private fmtPct(v?: number | null): string {
    return typeof v === 'number' ? `${v.toFixed(1)}%` : '—';
  }

  /** EXPort HTML complet (KPI + sections + tableau aligné sur TicketTable) */
  exportAllToHtml(): void {
    const kpi = this.sprintInfo?.sprintKpiInfo;
    const commit = this.sprintInfo?.sprintCommitInfo;

    const sections = [
      { title: 'Tickets engagés au début', tickets: commit?.committedAtStart || [] },
      { title: 'Tickets ajoutés pendant le sprint', tickets: commit?.addedDuring || [] },
      { title: 'Tickets retirés du sprint', tickets: commit?.removedDuring || [] },
    ];

    let htmlContent = `
    <html lang="fr">
      <head>
        <meta charset="utf-8" />
        <title>Export Sprint Scope</title>
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <style>${this.baseStyles()}</style>
      </head>
      <body>
        <h1>Indicateurs du Sprint</h1>
        <table class="kpi-table">
          <tbody>
            <tr><td>Engagement respecté</td><td>${this.fmtPct(kpi?.engagementRespectePourcent)}</td></tr>
            <tr><td>Ajouts non prévus</td><td>${this.fmtPct(kpi?.ajoutsNonPrevusPourcent)}</td></tr>
            <tr><td>Engagés non terminés</td><td>${this.fmtPct(kpi?.nonTerminesEngagesPourcent)}</td></tr>
            <tr><td>Succès global</td><td>${this.fmtPct(kpi?.succesGlobalPourcent)}</td></tr>
            <tr><td>Tickets engagés</td><td>${kpi?.committedAtStart ?? '—'}</td></tr>
            <tr><td>Tickets ajoutés</td><td>${kpi?.addedDuring ?? '—'}</td></tr>
            <tr><td>Tickets retirés</td><td>${kpi?.removedDuring ?? '—'}</td></tr>
            <tr><td>Tickets terminés</td><td>${kpi?.doneTickets ?? '—'}</td></tr>
            <tr><td>Dev terminés avant sprint</td><td>${kpi?.devDoneBeforeSprint ?? '—'}</td></tr>
          </tbody>
        </table>

        <h1>Scope du sprint</h1>
        ${sections.map(s => `<h2>${s.title}</h2>${this.generateHtmlTable(s.tickets)}`).join('')}
      </body>
    </html>`.trim();

    // --- Téléchargement en .html (UTF-8 + BOM pour les accents) ---
    const filename = `sprint-scope-${this.slugify(this.sprintId || 'export')}-${new Date().toISOString().slice(0,10)}.html`;
    this.downloadFile(filename, htmlContent, 'text/html;charset=utf-8');
  }

  /** Télécharge un fichier via Blob + lien temporaire */
  private downloadFile(filename: string, content: string, mime: string): void {
    const bom = '\ufeff'; // BOM pour assurer l’UTF-8 (accentuation)
    const blob = new Blob([bom, content], { type: mime });
    const url = URL.createObjectURL(blob);

    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();

    // Nettoyage
    setTimeout(() => {
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    }, 0);
  }

  /** Nom de fichier safe */
  private slugify(s: string): string {
    return s
      .normalize('NFD').replace(/[\u0300-\u036f]/g, '')   // accents
      .replace(/[^a-zA-Z0-9-_]+/g, '-')                  // caractères spéciaux
      .replace(/-+/g, '-')                                // tirets multiples
      .replace(/^-|-$/g, '')                              // bords
      .toLowerCase();
  }

  /** Tableau export : colonnes identiques à TicketTable (link + done) */
  private generateHtmlTable(tickets: Ticket[]): string {
    if (!tickets || tickets.length === 0) {
      return '<p><i>Aucun ticket.</i></p>';
    }

    const header = `
      <thead>
        <tr>
          <th>Clé</th>
          <th>Assigné</th>
          <th>Status</th>
          <th>Pts</th>
          <th>Pts réalisés avant sprint</th>
          <th>Engagement Sprint</th>
          <th>Type</th>
          <th>Version corrigée</th>
          <th>Terminé</th>
        </tr>
      </thead>
    `;

    const rows = tickets.map(t => {
      const isDone = this.isTicketDoneForExport(t);
      const pts = (t.storyPoints ?? '') as any;
      const ptsAvant = (t.storyPointsRealiseAvantSprint ?? '') as any;

      return `
        <tr>
          <td class="cell-key">
            ${t.url
              ? `<a href="${t.url}" target="_blank" rel="noopener noreferrer" title="Ouvrir dans Jira">${t.ticketKey}</a>`
              : `${t.ticketKey ?? ''}`
            }
          </td>
          <td>${t.assignee ?? ''}</td>
          <td>${t.status ?? ''}</td>
          <td>${pts}</td>
          <td>${ptsAvant}</td>
          <td>${t.engagementSprint ?? ''}</td>
          <td>${t.type ?? ''}</td>
          <td>${t.versionCorrigee ?? ''}</td>
          <td class="cell-done">
            <span class="chip ${isDone ? 'chip--ok' : 'chip--ko'}" aria-label="${isDone ? 'Terminé' : 'Non terminé'}">
              ${isDone ? '✔' : '✘'}
            </span>
          </td>
        </tr>
      `;
    }).join('');

    return `<table class="tickets-table">${header}<tbody>${rows}</tbody></table>`;
  }

  /** Styles simplifiés, lisibles en export (zébrage, chips, liens, KPI) */
  private baseStyles(): string {
    return `
    :root{
      --ok:#2e7d32; --ko:#c62828; --text:#2c3e50; --muted:#6b778c; --border:#e0e6ef; --bg:#fff;
    }
    *{box-sizing:border-box}
    body{font-family:Arial, sans-serif; padding:20px; color:var(--text); background:#fafbfc}
    h1,h2{margin:0 0 10px}
    h1{font-size:20px; border-bottom:2px solid var(--border); padding-bottom:6px}
    h2{font-size:16px; color:#1a237e; border-bottom:1px solid var(--border); padding-bottom:4px; margin-top:28px}

    table{border-collapse:collapse; width:100%; margin:14px 0 24px; background:var(--bg)}
    th,td{border:1px solid var(--border); padding:8px; text-align:left; font-size:13px}
    th{background:#f4f6fa; font-weight:600}
    .tickets-table tr:nth-child(even){background:#fbfdff}
    .kpi-table td:first-child{color:var(--muted)}
    .cell-key a{color:#1565c0; text-decoration:none}
    .cell-key a:hover{text-decoration:underline}

    .cell-done{text-align:center}
    .chip{display:inline-block; min-width:1.6em; text-align:center; padding:2px 8px; border-radius:999px; font-weight:700}
    .chip--ok{background:#e8f5e9; color:var(--ok); border:1px solid #c8e6c9}
    .chip--ko{background:#ffebee; color:var(--ko); border:1px solid #ffcdd2}
    `;
  }

  async exportAllToXlsx(): Promise<void> {
    const xlsx = await import('xlsx');

    const kpi = this.sprintInfo?.sprintKpiInfo;
    const commit = this.sprintInfo?.sprintCommitInfo;

    const wb = xlsx.utils.book_new();

    // --- Feuille KPI
    const kpiAoA = [
      ['Indicateur', 'Valeur'],
      ['Engagement respecté', this.fmtPct(kpi?.engagementRespectePourcent)],
      ['Ajouts non prévus',   this.fmtPct(kpi?.ajoutsNonPrevusPourcent)],
      ['Engagés non terminés',this.fmtPct(kpi?.nonTerminesEngagesPourcent)],
      ['Succès global',       this.fmtPct(kpi?.succesGlobalPourcent)],
      ['Tickets engagés',     kpi?.committedAtStart ?? '—'],
      ['Tickets ajoutés',     kpi?.addedDuring ?? '—'],
      ['Tickets retirés',     kpi?.removedDuring ?? '—'],
      ['Tickets terminés',    kpi?.doneTickets ?? '—'],
      ['Dev terminés avant sprint', kpi?.devDoneBeforeSprint ?? '—'],
    ];
    const wsKpi = xlsx.utils.aoa_to_sheet(kpiAoA);
    xlsx.utils.book_append_sheet(wb, wsKpi, 'KPI');

    // --- Feuilles Tickets (3 sections)
    const sheets: Array<[string, Ticket[]]> = [
      ['Engagés',  commit?.committedAtStart || []],
      ['Ajoutés',  commit?.addedDuring || []],
      ['Retirés',  commit?.removedDuring || []],
    ];

    for (const [name, list] of sheets) {
      const rows = list.map(t => ({
        'Clé': t.ticketKey ?? '',
        'Assigné': t.assignee ?? '',
        'Status': t.status ?? '',
        'Pts': t.storyPoints ?? '',
        'Pts réalisés avant sprint': t.storyPointsRealiseAvantSprint ?? '',
        'Engagement Sprint': t.engagementSprint ?? '',
        'Type': t.type ?? '',
        'Version corrigée': t.versionCorrigee ?? '',
        'Terminé': this.isTicketDoneForExport(t) ? 'Oui' : 'Non',
        'Lien': t.url ?? ''
      }));
      const ws = xlsx.utils.json_to_sheet(rows);
      xlsx.utils.book_append_sheet(wb, ws, name);
    }

    const filename = `sprint-scope-${this.slugify(this.sprintId || 'export')}-${new Date().toISOString().slice(0,10)}.xlsx`;
    xlsx.writeFile(wb, filename);
  }

  async exportAllToPdf(): Promise<void> {
    const { jsPDF } = await import('jspdf');                 // named export
    const autoTable = (await import('jspdf-autotable')).default;

    const kpi = this.sprintInfo?.sprintKpiInfo;
    const commit = this.sprintInfo?.sprintCommitInfo;

    const doc = new jsPDF({ orientation: 'portrait', unit: 'pt', format: 'a4' });
    let cursorY = 40;

    // --- Titre
    doc.setFontSize(16);
    doc.text('Indicateurs du Sprint', 40, cursorY);
    cursorY += 10;

    // --- Tableau KPI
    const kpiBody = [
      ['Engagement respecté',        this.fmtPct(kpi?.engagementRespectePourcent)],
      ['Ajouts non prévus',          this.fmtPct(kpi?.ajoutsNonPrevusPourcent)],
      ['Engagés non terminés',       this.fmtPct(kpi?.nonTerminesEngagesPourcent)],
      ['Succès global',              this.fmtPct(kpi?.succesGlobalPourcent)],
      ['Tickets engagés',            `${kpi?.committedAtStart ?? '—'}`],
      ['Tickets ajoutés',            `${kpi?.addedDuring ?? '—'}`],
      ['Tickets retirés',            `${kpi?.removedDuring ?? '—'}`],
      ['Tickets terminés',           `${kpi?.doneTickets ?? '—'}`],
      ['Dev terminés avant sprint',  `${kpi?.devDoneBeforeSprint ?? '—'}`],
    ];

    autoTable(doc, {
      startY: cursorY + 10,
      head: [['Indicateur', 'Valeur']],
      body: kpiBody,
      styles: { fontSize: 10, cellPadding: 6 },
      headStyles: { fillColor: [244, 246, 250] },
      theme: 'grid',
      margin: { left: 40, right: 40 }
    });
    // @ts-ignore
    cursorY = (doc as any).lastAutoTable.finalY + 24;

    // --- Sections tickets
    const sections: Array<[string, Ticket[]]> = [
      ['Tickets engagés au début', commit?.committedAtStart || []],
      ['Tickets ajoutés pendant le sprint', commit?.addedDuring || []],
      ['Tickets retirés du sprint', commit?.removedDuring || []],
    ];

    for (const [title, list] of sections) {
      if (cursorY > doc.internal.pageSize.getHeight() - 120) {
        doc.addPage();
        cursorY = 40;
      }

      doc.setFontSize(14);
      doc.text(title, 40, cursorY);
      cursorY += 10;

      const head = [['Clé','Assigné','Status','Pts','Pts avant','Engagement','Type','Version','Terminé']];
      const body = list.map(t => ([
        t.ticketKey ?? '',
        t.assignee ?? '',
        t.status ?? '',
        t.storyPoints ?? '',
        t.storyPointsRealiseAvantSprint ?? '',
        t.engagementSprint ?? '',
        t.type ?? '',
        t.versionCorrigee ?? '',
        this.isTicketDoneForExport(t) ? '✔' : '✘'
      ]));

      autoTable(doc, {
        startY: cursorY + 10,
        head,
        body,
        styles: { fontSize: 9, cellPadding: 4 },
        headStyles: { fillColor: [244, 246, 250] },
        theme: 'grid',
        margin: { left: 40, right: 40 },
        didDrawPage: () => {
          doc.setFontSize(9);
          const header = `Sprint Scope - ${this.sprintId || ''}`;
          const date = new Date().toLocaleDateString();
          doc.text(header, 40, 20);
          doc.text(date, doc.internal.pageSize.getWidth() - 40, 20, { align: 'right' });
        }
      });
      // @ts-ignore
      cursorY = (doc as any).lastAutoTable.finalY + 24;
    }

    const filename = `sprint-scope-${this.slugify(this.sprintId || 'export')}-${new Date().toISOString().slice(0,10)}.pdf`;
    doc.save(filename);
  }

  // --- utilitaire: récupérer le PNG d’un chart ---
  private getChartPng(inst?: echarts.ECharts, pixelRatio = 2): string | null {
    if (!inst) return null;
    try {
      return inst.getDataURL({
        type: 'png',
        pixelRatio,
        backgroundColor: '#ffffff',
      });
    } catch {
      return null;
    }
  }

  // --- export PowerPoint ---
  async exportToPptx(): Promise<void> {
    const PptxGenJS = (await import('pptxgenjs')).default;
    const pptx = new PptxGenJS();
    pptx.layout = 'LAYOUT_16x9'; // 10in x 5.625in

    const filename = `sprint-scope-${this.slugify(this.sprintId || 'export')}-${new Date().toISOString().slice(0,10)}.pptx`;

    // ===== 1) Slide Titre =====
    let slide = pptx.addSlide();
    slide.addText('Sprint Scope', { x: 0.6, y: 0.5, fontSize: 26, bold: true });
    slide.addText(`Sprint : ${this.sprintId ?? ''}`, { x: 0.6, y: 1.1, fontSize: 14, color: '666666' });
    slide.addText(new Date().toLocaleDateString(), { x: 9.4, y: 0.6, fontSize: 10, color: '666666', align: 'right' });

    // ===== 2) Slide KPI (tableau) =====
    const kpi = this.sprintInfo?.sprintKpiInfo;
    const kpiRows: any[][] = [
      [
        { text: 'Indicateur', options: { bold: true, fill: { color: 'F4F6FA' } } },
        { text: 'Valeur',     options: { bold: true, fill: { color: 'F4F6FA' } } }
      ],
      ['Engagement respecté',        this.fmtPct(kpi?.engagementRespectePourcent)],
      ['Ajouts non prévus',          this.fmtPct(kpi?.ajoutsNonPrevusPourcent)],
      ['Engagés non terminés',       this.fmtPct(kpi?.nonTerminesEngagesPourcent)],
      ['Succès global',              this.fmtPct(kpi?.succesGlobalPourcent)],
      ['Tickets engagés',            `${kpi?.committedAtStart ?? '—'}`],
      ['Tickets ajoutés',            `${kpi?.addedDuring ?? '—'}`],
      ['Tickets retirés',            `${kpi?.removedDuring ?? '—'}`],
      ['Tickets terminés',           `${kpi?.doneTickets ?? '—'}`],
      ['Dev terminés avant sprint',  `${kpi?.devDoneBeforeSprint ?? '—'}`],
    ];
    slide = pptx.addSlide();
    slide.addText('Indicateurs du Sprint', {
      x: this.PPT.M.l, y: this.PPT.M.t, w: this.contentWidth, fontSize: 20, bold: true
    });
    slide.addTable(kpiRows, {
      x: this.PPT.M.l,
      y: this.PPT.M.t + 0.5,
      w: this.contentWidth,              // ← 8.8"
      colW: [5.0, this.contentWidth - 5.0], // optionnel ; sinon supprime colW
      fontSize: 12,
      border: { type: 'solid', color: 'C9D2E3', pt: 1 },
      fill: { color: 'FFFFFF' }
    });

    // ===== 3) Slides Diagrammes =====
    const imgKpi   = this.getChartPng(this.charts['kpi']);
    const imgTypeS = this.getChartPng(this.charts['typeSp']);
    const imgTypeN = this.getChartPng(this.charts['typeNb']);

    // 3.1 - KPI Pie
    if (imgKpi) {
      const ratio = this.chartRatio('kpi');
      const maxW = this.contentWidth;  // 8.8"
      const maxH = 5.0;
      const { w, h } = this.fitBox(ratio, maxW, maxH);
      const baseX = this.PPT.M.l, baseY = this.PPT.M.t + 0.4;
      const { x, y } = this.centerInBox(baseX, baseY, maxW, maxH, w, h);

      slide = pptx.addSlide();
      slide.addText('Diagramme : Indicateurs principaux', {
        x: this.PPT.M.l, y: this.PPT.M.t, w: this.contentWidth, fontSize: 18, bold: true
      });
      slide.addImage({ data: imgKpi, x, y, w, h });
    }

    // 3.2 - Répartition par type (deux graphes côte à côte si présents)
    if (imgTypeS || imgTypeN) {
      slide = pptx.addSlide();
      slide.addText('Répartition par type', { x: 0.6, y: 0.5, fontSize: 18, bold: true });

      const top = 1.0;
      const boxW = 4.3;
      const boxH = 4.3;

      if (imgTypeS && imgTypeN) {
        // Gauche
        {
          const ratio = this.chartRatio('typeSp');
          const { w, h } = this.fitBox(ratio, boxW, boxH);
          const { x, y } = this.centerInBox(0.6, top, boxW, boxH, w, h);
          slide.addImage({ data: imgTypeS, x, y, w, h });
          slide.addText('Story-Points par type', { x: 0.6, y: top + boxH + 0.1, fontSize: 12, color: '666666' });
        }
        // Droite
        {
          const ratio = this.chartRatio('typeNb');
          const { w, h } = this.fitBox(ratio, boxW, boxH);
          const { x, y } = this.centerInBox(5.3, top, boxW, boxH, w, h);
          slide.addImage({ data: imgTypeN, x, y, w, h });
          slide.addText('Nombre de tickets par type', { x: 5.3, y: top + boxH + 0.1, fontSize: 12, color: '666666' });
        }
      } else if (imgTypeS) {
        const ratio = this.chartRatio('typeSp');
        const { w, h } = this.fitBox(ratio, 9.0, 5.0);
        const { x, y } = this.centerInBox(0.6, top, 9.0, 5.0, w, h);
        slide.addImage({ data: imgTypeS, x, y, w, h });
        slide.addText('Story-Points par type', { x: 0.6, y: top + 5.0 + 0.1, fontSize: 12, color: '666666' });
      } else if (imgTypeN) {
        const ratio = this.chartRatio('typeNb');
        const { w, h } = this.fitBox(ratio, 9.0, 5.0);
        const { x, y } = this.centerInBox(0.6, top, 9.0, 5.0, w, h);
        slide.addImage({ data: imgTypeN, x, y, w, h });
        slide.addText('Nombre de tickets par type', { x: 0.6, y: top + 5.0 + 0.1, fontSize: 12, color: '666666' });
      }
    }

  // ===== 4) Slides Tableaux Tickets (paginés) =====
  const commit = this.sprintInfo?.sprintCommitInfo;
  const engaged = commit?.committedAtStart ?? [];
  const added   = commit?.addedDuring ?? [];
  // const total   = this.unionTickets(engaged, added);

  const tables: Array<{title: string; rows: any[][]}> = [
    { title: 'Tickets engagés au début',          rows: this.ticketsToTableRows(engaged) },
    { title: 'Tickets ajoutés pendant le sprint', rows: this.ticketsToTableRows(added) },
    // { title: 'Tickets TOTAL (engagés ∪ ajoutés)', rows: this.ticketsToTableRows(total) },
  ];

  // --- Paramètres de mise en page (LAYOUT_16x9 = 10" x 5.625")
  const MARGIN_L = 0.6;
  const MARGIN_R = 0.6;
  const MARGIN_T = 0.6;
  const CONTENT_W = 10 - MARGIN_L - MARGIN_R;  // 8.8"

  // --- Pagination : nb de lignes de corps par slide (hors en-tête)
  const MAX_BODY_ROWS = 10;

  // (Optionnel) Largeurs de colonnes si tu veux forcer la répartition
  const COL_W = [1.1, 1.5, 1.6, 0.6, 0.9, 1.1, 1.1, 1.2, 0.8];
  //    Ordre:   Clé, Assigné, Status, Pts, Pts avant, Engagement, Type, Version, Terminé

  for (const { title, rows } of tables) {
    if (!rows?.length) continue;

    const [head, ...body] = rows;
    for (let i = 0; i < body.length || i === 0; i += Math.max(1, MAX_BODY_ROWS)) {
      // bodyPart gère aussi le cas liste vide (on affiche uniquement l'en-tête)
      const bodyPart = body.length ? body.slice(i, i + MAX_BODY_ROWS) : [];
      const pageRows = [head, ...bodyPart];

      const slide = pptx.addSlide();

      // Titre (aligné sur la largeur utile)
      slide.addText(i === 0 ? title : `${title} — suite ${Math.floor(i / MAX_BODY_ROWS) + 1}`, {
        x: MARGIN_L, y: MARGIN_T, w: CONTENT_W,
        fontSize: 18, bold: true
      });

      // Tableau paginé avec padding (marges)
      slide.addTable(pageRows, {
        x: MARGIN_L,
        y: MARGIN_T + 0.4,     // petit espace sous le titre
        w: CONTENT_W,
        colW: COL_W,           // supprime cette ligne si tu préfères auto
        fontSize: 10,
        border: { type: 'solid', color: 'C9D2E3', pt: 1 },
        fill: { color: 'FFFFFF' }
      });

      // Si tu veux encore plus d’espace en bas, baisse MAX_BODY_ROWS (ex. 15)
    }
  }

    // ===== 5) Slides "Par type": Engagés / Ajoutés / Total (paginés) =====
    const kpis = this.sprintInfo?.sprintKpiInfo;

    // Fallback Total = Committed + Added si typeCountAll absent
    // const totalTypeCounts: TypeCountMap =
    //   (kpis?.typeCountAll as TypeCountMap) ??
    //   this.mergeTypeCounts(
    //     kpis?.typeCountCommitted as TypeCountMap,
    //     kpis?.typeCountAdded as TypeCountMap
    //   );

    const tablesByType: Array<{ title: string; rows: any[][]; enabled: boolean }> = [
      { title: 'Par type — Engagés', rows: this.typeCountsToRows(kpis?.typeCountCommitted as TypeCountMap), enabled: this.showCommittedByType !== false },
      { title: 'Par type — Ajoutés', rows: this.typeCountsToRows(kpis?.typeCountAdded as TypeCountMap),    enabled: this.showAddedByType !== false },
    ];

    for (const t of tablesByType) {
      if (!t.enabled) continue;
      this.addPagedTable(pptx, t.title, t.rows, {
        maxBodyRows: 20,
        fontSize: 11
      });
    }

    await pptx.writeFile({ fileName: filename });
  }

  /** Renvoie le ratio W/H du chart (fallback 16/9) */
  private chartRatio(id: 'kpi' | 'typeSp' | 'typeNb'): number {
    const ec = this.charts[id];
    if (!ec) return 16 / 9;
    const w = ec.getWidth() || 1600;
    const h = ec.getHeight() || 900;
    return w / h;
  }

  /** Calcule w/h qui tiennent dans maxW/maxH en conservant le ratio */
  private fitBox(ratio: number, maxW: number, maxH: number): { w: number; h: number } {
    let w = maxW;
    let h = w / ratio;
    if (h > maxH) {
      h = maxH;
      w = h * ratio;
    }
    return { w, h };
  }

  /** Centre un rectangle (w,h) dans un box (x,y,maxW,maxH) -> nouvelles coords */
  private centerInBox(x: number, y: number, maxW: number, maxH: number, w: number, h: number): { x: number; y: number } {
    const dx = (maxW - w) / 2;
    const dy = (maxH - h) / 2;
    return { x: x + Math.max(0, dx), y: y + Math.max(0, dy) };
  }

  /** Union (dédup par ticketKey) pour le tableau "Total" */
  private unionTickets(a: Ticket[] = [], b: Ticket[] = []): Ticket[] {
    const map = new Map<string, Ticket>();
    for (const t of a) if (t?.ticketKey) map.set(t.ticketKey, t);
    for (const t of b) if (t?.ticketKey && !map.has(t.ticketKey)) map.set(t.ticketKey, t);
    return Array.from(map.values());
  }

  /** Convertit une liste de tickets en lignes de tableau PptxGenJS */
  private ticketsToTableRows(list: Ticket[]): any[][] {
    const head: any[] = [
      { text: 'Clé', options: { bold: true, fill: { color: 'F4F6FA' } } },
      { text: 'Assigné', options: { bold: true, fill: { color: 'F4F6FA' } } },
      { text: 'Status', options: { bold: true, fill: { color: 'F4F6FA' } } },
      { text: 'Pts', options: { bold: true, fill: { color: 'F4F6FA' } } },
      { text: 'Type', options: { bold: true, fill: { color: 'F4F6FA' } } },
      { text: 'Version', options: { bold: true, fill: { color: 'F4F6FA' } } },
      { text: 'Terminé', options: { bold: true, fill: { color: 'F4F6FA' } } },
    ];

    const body = list.map(t => {
      const done = this.isTicketDoneForExport(t) ? '✔' : '✘';
      const keyCell = t?.url
        ? { text: t.ticketKey ?? '', options: { hyperlink: { url: t.url }, underline: true, color: '1155CC' } }
        : { text: t.ticketKey ?? '' };

      return [
        keyCell,
        t?.assignee ?? '',
        t?.status ?? '',
        t?.storyPoints ?? '',
        t?.type ?? '',
        t?.versionCorrigee ?? '',
        done,
      ];
    });

    return [head, ...body];
  }

  /** Fusionne 2 maps de typeCounts (somme nbTickets + nbStoryPoints) */
  private mergeTypeCounts(a?: TypeCountMap, b?: TypeCountMap): TypeCountMap {
    const res: TypeCountMap = {};
    for (const [k, v] of Object.entries(a ?? {})) {
      res[k] = { nbTickets: v.nbTickets ?? 0, nbStoryPoints: v.nbStoryPoints ?? 0 };
    }
    for (const [k, v] of Object.entries(b ?? {})) {
      if (!res[k]) res[k] = { nbTickets: 0, nbStoryPoints: 0 };
      res[k].nbTickets += v.nbTickets ?? 0;
      res[k].nbStoryPoints += v.nbStoryPoints ?? 0;
    }
    return res;
  }

  /** Convertit un TypeCountMap en lignes de tableau PptxGenJS (tri par SP desc) */
  private typeCountsToRows(map?: TypeCountMap): any[][] {
    const head: any[] = [
      { text: 'Type',          options: { bold: true, fill: { color: 'F4F6FA' } } },
      { text: 'nb tickets',    options: { bold: true, fill: { color: 'F4F6FA' } } },
      { text: 'story points',  options: { bold: true, fill: { color: 'F4F6FA' } } },
    ];
    if (!map || Object.keys(map).length === 0) {
      return [head, ['—', '—', '—']];
    }
    const entries = Object.entries(map)
      .sort((a, b) => (b[1]?.nbStoryPoints ?? 0) - (a[1]?.nbStoryPoints ?? 0));

    const body = entries.map(([type, v]) => [
      type,
      `${v.nbTickets ?? 0}`,
      `${v.nbStoryPoints ?? 0}`,
    ]);

    return [head, ...body];
  }

  /** Découpe [head + body...] en plusieurs tableaux avec en-tête répété */
  private chunkTableRows(rows: any[][], maxBodyRowsPerSlide: number): any[][][] {
    if (!rows?.length) return [];
    const [head, ...body] = rows;
    if (maxBodyRowsPerSlide <= 0) return [rows];

    const chunks: any[][][] = [];
    for (let i = 0; i < body.length; i += maxBodyRowsPerSlide) {
      const part = body.slice(i, i + maxBodyRowsPerSlide);
      chunks.push([head, ...part]);
    }
    return chunks;
  }


  /** Ajoute un tableau paginé avec marges latérales et espace en bas */
  private addPagedTable(
    pptx: any,
    title: string,
    rows: any[][],
    opts?: {
      maxBodyRows?: number;   // nb de lignes (hors en-tête) par slide
      x?: number; y?: number; w?: number;
      fontSize?: number;
      colW?: number[];
    }
  ): void {
    const {
      maxBodyRows = 17,                 // ↓ baisse si tu veux plus d’espace en bas
      x = this.PPT.M.l,                 // marge gauche = 0.6"
      y = this.PPT.M.t + 0.4,           // 0.4" sous le titre
      w = this.contentWidth,            // largeur utile = 8.8"
      fontSize = 10,
      colW
    } = opts || {};

    const pages = this.chunkTableRows(rows, maxBodyRows);
    pages.forEach((pageRows, idx) => {
      const slide = pptx.addSlide();
      // Titre sur toute la largeur utile (pour align right si besoin)
      slide.addText(idx === 0 ? title : `${title} — suite ${idx + 1}`, {
        x: this.PPT.M.l, y: this.PPT.M.t, w: this.contentWidth,
        fontSize: 18, bold: true
      });
      slide.addTable(pageRows, {
        x, y, w,
        ...(colW ? { colW } : {}),
        fontSize,
        border: { type: 'solid', color: 'C9D2E3', pt: 1 },
        fill: { color: 'FFFFFF' }
      });
    });
  }




// --- Ajoute ces helpers dans ta classe ---
/** Récupère le noeud qui contient uniquement les tableaux d’indicateurs */
private getKpiTablesElement(): HTMLElement | null {
  // On vise le bloc sans l’en-tête/boutons : exactement <div class="kpi-main-layout">
  return document.querySelector('.kpi-main-layout') as HTMLElement | null;
}

/** Charge un dataURL en <img> pour en lire les dimensions pixels */
private dataUrlToDims(dataUrl: string): Promise<{ width: number; height: number }> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve({ width: img.naturalWidth, height: img.naturalHeight });
    img.onerror = (e) => reject(e);
    img.src = dataUrl;
  });
}

/** Capture le DOM en PNG (pixelRatio>=2 pour la netteté) */
private async captureKpiTablesPng(pixelRatio = 2): Promise<string | null> {
  const node = this.getKpiTablesElement();
  if (!node) return null;

  // Import dynamique pour ne pas alourdir le bundle au chargement
  const { toPng } = await import('html-to-image');

  // Option : on fixe un fond blanc (utile si ton thème a du transparent)
  const dataUrl = await toPng(node, {
    pixelRatio,
    backgroundColor: '#ffffff',
    cacheBust: true
  });
  return dataUrl;
}

/** Télécharger le PNG localement (si tu veux juste l’image) */
public async downloadKpiTablesPng(): Promise<void> {
  const dataUrl = await this.captureKpiTablesPng(2);
  if (!dataUrl) return;

  const a = document.createElement('a');
  a.href = dataUrl;
  a.download = `kpi-tables-${new Date().toISOString().slice(0,10)}.png`;
  a.click();
}



/** Insère la capture PNG des tableaux dans un PPT (sans modifier les tableaux TS) */
public async exportToPptxWithKpiScreenshot(): Promise<void> {
  const dataUrl = await this.captureKpiTablesPng(2);
  if (!dataUrl) return;

  const { width, height } = await this.dataUrlToDims(dataUrl);
  const ratio = width / height;

  const PptxGenJS = (await import('pptxgenjs')).default;
  const pptx = new PptxGenJS();
  pptx.layout = 'LAYOUT_16x9';

  // Slide titre rapide
  let slide = pptx.addSlide();
  slide.addText('Sprint Scope – Tableaux indicateurs (capture)', {
    x: this.PPT.M.l, y: this.PPT.M.t, w: this.contentWidth, fontSize: 20, bold: true
  });
  slide.addText(new Date().toLocaleDateString(), {
    x: this.PPT.W - this.PPT.M.r, y: this.PPT.M.t, fontSize: 10, color: '666666', align: 'right'
  });

  // On “fit” l’image dans une zone utile (largeur 8.8", hauteur ~5")
  const maxW = this.contentWidth; // 8.8"
  const maxH = 5.0;
  let w = maxW, h = w / ratio;
  if (h > maxH) { h = maxH; w = h * ratio; }

  // Centrage dans la zone
  const x = this.PPT.M.l + (maxW - w) / 2;
  const y = this.PPT.M.t + 0.5 + (maxH - h) / 2;

  // Insertion de l’image
  slide.addImage({ data: dataUrl, x, y, w, h });

  // Sauvegarde
  const filename = `sprint-kpi-screenshot-${this.slugify(this.sprintId || 'export')}-${new Date().toISOString().slice(0,10)}.pptx`;
  await pptx.writeFile({ fileName: filename });
}





  

}
