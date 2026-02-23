import {
  Component,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  OnInit,
  ViewChild,
  ElementRef,
  QueryList,
  OnDestroy,
  inject
} from '@angular/core';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormControl } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DATE_FORMATS, MAT_NATIVE_DATE_FORMATS, provideNativeDateAdapter } from '@angular/material/core';
import { MatDialog, MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { OverlayContainer } from '@angular/cdk/overlay';

import { forkJoin, of, Subscription } from 'rxjs';

/* === Domain models & utils === */
import { CalendarVO } from '../../model/CalendarVO';
import { Month, Resource, Holiday, Event } from '../../model/Resource';

import { CalendarService } from '../../service/calendar.service';
import { HolidayService } from '../../service/HolidayService';
import { ResourceService } from '../../service/resource.service';
import { DateUtils } from '../utils/date-utils';
import html2pdf from 'html2pdf.js';
import { EventService } from '../../service/event.service';

/* -------------------------------------------------------------------------- */
/*                                 Component                                  */
/* -------------------------------------------------------------------------- */
@Component({
  selector: 'app-calendar',
  templateUrl: './calendar.component.html',
  styleUrls: ['./calendar.component.css', 'calendar.component.dark-mode.scss'],
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    /* Material */
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatIconModule,
    MatDividerModule,
    MatButtonModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CalendarComponent implements OnInit, OnDestroy {
  /* ---------------------------------------------------------------------- */
  /* 🛠 Template helpers                                                     */
  /* ---------------------------------------------------------------------- */
  DateUtils = DateUtils;
  Math = Math;
  MAX_CACHE_MONTHS = 6;   // garde au plus 6 mois en mémoire

  /* ---------------------------------------------------------------------- */
  /* 🔗 ViewChild                                                            */
  /* ---------------------------------------------------------------------- */
  @ViewChild('monthDivs') monthDivs!: QueryList<ElementRef>;
  @ViewChild('calendarRef', { static: false }) calendarRef!: ElementRef<HTMLElement>;

  /* ---------------------------------------------------------------------- */
  /* 📊 Data                                                                 */
  /* ---------------------------------------------------------------------- */
  months: Month[] = [];
  calendarVos: CalendarVO[] = [];
  resources: Resource[] = [];
  holidays: Holiday[] = [];

  currentYear = new Date().getFullYear();
  selectedMonth = new Date().getMonth() + 1;

  /* ---------------------------------------------------------------------- */
  /* ⚙️  Caches & maps                                                       */
  /* ---------------------------------------------------------------------- */
  /**
   * Cache des spans par mois sous la forme "YYYY-MM" → Map<prenomResource, EventSpan[]>
   * Permet d'éviter un recalcul coûteux à chaque navigation.
   */
  spanCache = new Map<string, Map<string, EventSpan[]>>();
  /** Présences calculées : prenomResource → (indexMois → nb jours) */
  private presenceDaysCache = new Map<string, Map<number, number>>();

  /* ---------------------------------------------------------------------- */
  /* 🎨 UI state                                                             */
  /* ---------------------------------------------------------------------- */
  hoveredCell: { tableIdx: number; row: number; col: number } | null = null;
  showConge = true;
  showTeletravail = true;

  /* ---------------------------------------------------------------------- */
  /* 🔄 Subscriptions                                                        */
  /* ---------------------------------------------------------------------- */
  private dataSub?: Subscription;

  /* ---------------------------------------------------------------------- */
  /* 🏗️ DI                                                                  */
  /* ---------------------------------------------------------------------- */
  constructor(
    private readonly calendarSvc: CalendarService,
    private readonly resourceSvc: ResourceService,
    private readonly eventSvc: EventService,
    private readonly holidaySvc: HolidayService,
    private readonly dialog: MatDialog,
    private readonly overlay: OverlayContainer,
    private readonly cdr: ChangeDetectorRef
  ) {
    this.overlay.getContainerElement().classList.add('dark-mode');
  }

  /* ---------------------------------------------------------------------- */
  /* 💡 Lifecycle                                                            */
  /* ---------------------------------------------------------------------- */
  ngOnInit(): void {
    this.initMonths();
    this.fetchInitial();
  }

  ngOnDestroy(): void {
    this.dataSub?.unsubscribe();
  }

  /* ---------------------------------------------------------------------- */
  /* 🏁 Initialisation                                                       */
  /* ---------------------------------------------------------------------- */
  private initMonths(): void {
    const labels = [
      'Janvier','Février','Mars','Avril','Mai','Juin',
      'Juillet','Aout','Septembre','Octobre','Novembre','Décembre'
    ];
    this.months = labels.map((lib, i) => new Month(i + 1, lib));
  }

  private fetchInitial(): void {
    this.dataSub?.unsubscribe();
    this.dataSub = forkJoin({
      resources: this.resourceSvc.getResources(),
      holidays:  of(this.holidaySvc.getHolidays(this.currentYear))
    }).subscribe(({ resources, holidays }) => {
      // normalise les dates des events
      resources.forEach(r => r.events.forEach(e => {
        e.dateDebutEvent = new Date(`${e.dateDebutEvent}T00:00:00`);
        e.dateFinEvent   = new Date(`${e.dateFinEvent}T00:00:00`);
      }));

  
    resources.forEach(r => {
        r.prenomResource = r.prenomResource.replace('Assih Jean-Samuel', 'Jean-Samuel');
  
    });

      this.resources = resources;
      this.holidays  = holidays;
      this.buildCalendar();
    });
  }

  /* ---------------------------------------------------------------------- */
  /* 🗓️ Construction du calendrier                                          */
  /* ---------------------------------------------------------------------- */
  private buildCalendar(): void {
    const vos: CalendarVO[] = [];
    const keys: string[] = [];

    for (let off = 0; off < 3; off++) {
      const date  = new Date(this.currentYear, this.selectedMonth - 1 + off);
      const month = date.getMonth() + 1;
      const year  = date.getFullYear();
      const key   = `${year}-${month}`;
      keys.push(key);

      const dates = this.calendarSvc.getDatesOfMonth(year, month);
      const vo    = new CalendarVO(month, formatDate(dates[0], 'MMMM', 'fr'), dates);
      vo.dayLabels = dates.map(d => d.getDate().toString().padStart(2,'0'));
      vos.push(vo);
    }

    /* 1️⃣  calendrier en place -> activeResources devient fiable */
    this.calendarVos = vos;

    /* 2️⃣  snapshot des ressources actives */
    const activeRes = this.activeResources;

    /* 3️⃣  remplir / mettre à jour le cache spans */
    vos.forEach((vo, idx) => {
      const firstDate = vo.dates[0];
      const key = `${firstDate.getFullYear()}-${firstDate.getMonth() + 1}`;

      if (!this.spanCache.has(key)) {
        this.spanCache.set(key,
          this.calendarSvc.buildSpansForMonth(vo.dates, activeRes)
        );
        // this.trimSpanCache();
      }
    });

    this.computePresence(this.getDisplayedMonthKeys());
    this.cdr.markForCheck();
  }

  /** Renvoie la liste des spans pour une ressource et un mois */
  getSpans(resource: Resource, monthIdx: number): EventSpan[] {
    const key = this.voKey(monthIdx);
    return this.spanCache.get(key)?.get(resource.prenomResource) ?? [];
  }

  /**
   * Recalcule la présence à partir du cache de spans.
   */
  private computePresence(_ignored?: string[]): void {
    this.presenceDaysCache.clear();

    // Les clés dans l’ordre d’affichage
    const orderedKeys = this.getDisplayedMonthKeys();

    for (const res of this.activeResources) {
      const monthMap = new Map<number, number>();

      orderedKeys.forEach((key, displayIdx) => {
        const spans = this.spanCache.get(key)?.get(res.prenomResource) ?? [];
        let total = 0;
        spans.forEach(s => {
          const d = s.date;
          const working = DateUtils.isWeekend(d) || d.isHoliday() || d.isInactif(res) || (d.isEvent(res) && !d.isDemiJourneeEvent(res));
          if (!working) total += d.isDemiJourneeEvent(res) ? 0.5 : 1;
        });
        monthMap.set(displayIdx, total);
      });

      this.presenceDaysCache.set(res.prenomResource, monthMap);
    }
  }

  /* ---------------------------------------------------------------------- */
  /* ➡️ Navigation mois                                                     */
  /* ---------------------------------------------------------------------- */
  prevMonth(): void {
    if (--this.selectedMonth === 0) { this.selectedMonth = 12; this.currentYear--; }
    this.buildCalendar();
  }

  nextMonth(): void {
    if (++this.selectedMonth === 13) { this.selectedMonth = 1; this.currentYear++; }
    this.buildCalendar();
  }

  onMonthChange(value: number): void {
    this.selectedMonth = +value;
    this.buildCalendar();
  }

  /* ---------------------------------------------------------------------- */
  /* 🙋‍♂️ Interactions                                                      */
  /* ---------------------------------------------------------------------- */
  /** Clic unique : si event → confirmation + suppression, sinon → création */
  onCellClick(res: Resource, date: Date): void {
    const dc = date as DateCalendar;

    if (dc.isEvent(res)) {
      const ev = dc.getEventFor(res);
      if (!ev) return;

      const ref = this.dialog.open(ConfirmDeleteDialog, {
        data: {
          resource: res.prenomResource,
          start: new Date(ev.dateDebutEvent!),
          end:   new Date(ev.dateFinEvent!)
        }
      });

      ref.afterClosed().subscribe((confirm) => {
        if (!confirm) return;

        const id = (ev as any).idEvent ?? (ev as any).id ?? null;
        if (!id) {
          console.error('Impossible de supprimer: id manquant sur Event');
          return;
        }

        this.eventSvc.deleteEvent(id).subscribe({
          next: () => {
            // 1) Retire localement l’event
            res.events = res.events.filter(e => (e as any).id !== id && (e as any).idEvent !== id);

            // 2) Clés de mois impactés
            const toMidnight = (d: Date | string) => { const x = new Date(d); x.setHours(0,0,0,0); return x; };
            const start = toMidnight(ev.dateDebutEvent!);
            const end   = toMidnight(ev.dateFinEvent!);
            const keys  = this.calcMonthKeys(start, end);

            // 3) Invalide et reconstruit
            keys.forEach(k => this.invalidateMonthCache(k));
            this.rebuildMonths(keys);
          },
          error: (err) => {
            console.error('deleteEvent failed', err);
          }
        });
      });

      return;
    }

    // Sinon: comportement existant (création)
    this.openDialog(res, date);
  }

  openDialog(res: Resource, date: Date): void {
    const ref = this.dialog.open(DialogEvent, {
      data: { dateSelectedDebut: date, resource: res.prenomResource }
    });

    ref.afterClosed().subscribe((ret) => {
      if (!ret) { return; }

      /* 1) construit l’objet Event localement */
      const ev = new Event();
      ev.dateDebutEvent = date;                    // déjà un Date
      ev.dateFinEvent   = ret.dateSelected as Date;
      ev.developper     = res;
      ev.isJournee      = !!ret.isJournee;
      ev.isMatin        = !!ret.isMatin;
      ev.isApresMidi    = !!ret.isApresMidi;

      /* 2) clé cache du mois de début */
      const key = `${date.getFullYear()}-${date.getMonth() + 1}`;



      /* 4) Enregistre en base ; si le back renvoie l’event, on le normalise  */
      function toYMDLocal(d: string | Date): string {
        const x = new Date(d);
        const yyyy = x.getFullYear();
        const mm = String(x.getMonth() + 1).padStart(2, '0');
        const dd = String(x.getDate()).padStart(2, '0');
        return `${yyyy}-${mm}-${dd}`;
      }
      this.eventSvc.saveEvent({
        ...ev,
        dateDebutEvent: toYMDLocal(ev.dateDebutEvent!),
        dateFinEvent:   toYMDLocal(ev.dateFinEvent!),
        developper: { idResource: res.idResource }
      }).subscribe({
        next: (saved) => {
          /* 1️⃣  normalise → Date locales minuit */
          const toMidnight = (iso: string | Date) => {
            const d = new Date(iso); d.setHours(0,0,0,0); return d;
          };
          saved.dateDebutEvent = toMidnight(saved.dateDebutEvent!);
          saved.dateFinEvent   = toMidnight(saved.dateFinEvent!);

          /* 2️⃣  ajoute réellement l’event dans la ressource */
          res.events = res.events.filter(e =>
            e.dateDebutEvent && e.dateFinEvent && saved.dateDebutEvent && saved.dateFinEvent &&
            !(e.dateDebutEvent.getTime() === saved.dateDebutEvent.getTime() &&
              e.dateFinEvent.getTime()   === saved.dateFinEvent.getTime())
          );
          res.events.push(saved);

          /* 3️⃣  invalide les mois couverts */
          const keys = new Set<string>();
          for (let d = new Date(saved.dateDebutEvent); d <= saved.dateFinEvent; d.setMonth(d.getMonth()+1, 1)) {
            keys.add(`${d.getFullYear()}-${d.getMonth()+1}`);
          }
          keys.forEach(k => this.invalidateMonthCache(k));

          /* 4️⃣  reconstruit le calendrier */
          this.rebuildMonths(keys);
        },

        error: (err) => {
          console.error('saveEvent failed', err);
          /* rollback : retire l’event optimiste si besoin */
          res.events = res.events.filter(e => e !== ev);
          this.invalidateMonthCache(key);
          this.buildCalendar();
        }
      });
    });
  }

  private calcMonthKeys(start: Date, end: Date): Set<string> {
    const keys = new Set<string>();
    const d = new Date(start);
    d.setDate(1);                           // jour 1 pour éviter boucle infinie
    while (d <= end) {
      keys.add(`${d.getFullYear()}-${d.getMonth() + 1}`);
      d.setMonth(d.getMonth() + 1, 1);      // passe au mois suivant
    }
    return keys;
  }

  private rebuildMonths(keys: Set<string>): void {
    /** 1) Met à jour CalendarVO uniquement pour les clés demandées */
    this.calendarVos.forEach((vo, idx) => {
      const voKey = this.voKey(idx);             // ex. '2025-6'
      if (!keys.has(voKey)) return;              // ← on laisse les autres intacts

      const dates = this.calendarSvc.getDatesOfMonth(
        vo.dates[0].getFullYear(),
        vo.dates[0].getMonth() + 1
      );

      vo.dates      = dates;
      vo.dayLabels  = dates.map(d => d.getDate().toString().padStart(2, '0'));

      // spans mis en cache (après invalidation)
      this.spanCache.set(
        voKey,
        this.calendarSvc.buildSpansForMonth(dates, this.activeResources)
      );
    });

    /** 2) Recalcule la présence seulement pour ces mois */
    this.computePresence(this.getDisplayedMonthKeys());
    this.cdr.markForCheck();
  }

  exportPdf(): void {
    if (!this.calendarRef) return;
    const el = this.calendarRef.nativeElement;
    el.classList.add('pdf-export');

    html2pdf()
      .from(el)
      .set({
        margin: 5,
        filename: `calendrier_${this.currentYear}_${this.selectedMonth}.pdf`,
        image: { type: 'jpeg', quality: 0.98 },
        html2canvas: { scale: 2 },
        jsPDF: { unit: 'mm', format: 'a4', orientation: 'landscape' }
      })
      .save()
      .finally(() => el.classList.remove('pdf-export'));
  }

  /* ---------------------------------------------------------------------- */
  /* 🧮 TrackBy                                                             */
  /* ---------------------------------------------------------------------- */
  trackMonth = (_: number, vo: CalendarVO) => vo.month;
  trackRes   = (_: number, r: Resource)    => r.prenomResource;
  trackDate  = (_: number, d: DateCalendar) => d.getTime();
  trackSpan  = (_: number, s: EventSpan)    => s.date.getTime();

  /* ---------------------------------------------------------------------- */
  /* 📌 Helpers                                                            */
  /* ---------------------------------------------------------------------- */
  get activeResources(): Resource[] {
    if (!this.calendarVos.length) return [];
    const dates = this.calendarVos[0].dates;
    return this.resources.filter(r => !this.calendarSvc.isResourceInactiveWholeMonth(r, dates));
  }

  getPresenceDays(res: Resource, idx: number): number {
    return this.presenceDaysCache.get(res.prenomResource)?.get(idx) ?? 0;
  }

  private getDisplayedMonthKeys(): string[] {
  return this.calendarVos.map(vo => {
    const d = vo.dates[0];
    return `${d.getFullYear()}-${d.getMonth() + 1}`;
  });
}

  getEmptyDays(count: number): number[] { return Array(count).fill(0); }

  isSpanOnWeekend(span: EventSpan, dates: DateCalendar[]): boolean {
    if (!span.colspan) return false;
    const startIdx = dates.findIndex(d => d.getTime() === span.date.getTime());
    for (let i = startIdx; i < startIdx + span.colspan && i < dates.length; i++) {
      const d = dates[i];
      if (!DateUtils.isWeekend(d) && !d.isHoliday()) return false;
    }
    return true;
  }

  /** Supprime les clés les plus anciennes si spanCache dépasse MAX_CACHE_MONTHS */
  private trimSpanCache(): void {
    while (this.spanCache.size > this.MAX_CACHE_MONTHS) {
      const oldestKey = [...this.spanCache.keys()]
        .sort((a, b) => {
          const [yA, mA] = a.split('-').map(Number);
          const [yB, mB] = b.split('-').map(Number);
          return yA !== yB ? yA - yB : mA - mB;
        })[0];

      this.spanCache.delete(oldestKey);
    }
  }

  /** Retourne la clé cache `YYYY-MM` pour l’index de mois affiché (0,1,2) */
  voKey(idx: number): string {
    if (!this.calendarVos[idx]) return '';
    const firstDate = this.calendarVos[idx].dates[0];
    return `${firstDate.getFullYear()}-${firstDate.getMonth() + 1}`;
  }

  /* ---------------------------------------------------------------------- */
  /* ✔ Utilities                                                           */
  /* ---------------------------------------------------------------------- */
  invalidateMonthCache(key: string): void { this.spanCache.delete(key); }
}

/* -------------------------------------------------------------------------- */
/*                            DialogEvent component                           */
/* -------------------------------------------------------------------------- */
@Component({
  selector: 'dialog-event',
  templateUrl: 'dialogEvent.html',
  styleUrls: ['dialogEvent.css'],
  standalone: true,
  imports: [
    MatDatepickerModule,
    MatCheckboxModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    FormsModule,
    ReactiveFormsModule
  ],
  providers: [
    provideNativeDateAdapter(),
    { provide: MAT_DATE_FORMATS, useValue: MAT_NATIVE_DATE_FORMATS }
  ]
})
export class DialogEvent {
  readonly dialogRef = inject(MatDialogRef<DialogEvent>);
  readonly data      = inject<DialogData>(MAT_DIALOG_DATA);
  readonly dateCtrl  = new FormControl<Date>(this.data.dateSelectedDebut);

  onNoClick(): void { this.dialogRef.close(); }
  onOkClick(): void {
    this.dialogRef.close({
      dateSelected: this.dateCtrl.value,
      isJournee:    this.data.isJournee,
      isMatin:      this.data.isMatin,
      isApresMidi:  this.data.isApresMidi
    });
  }
}

/* -------------------------------------------------------------------------- */
/*                        ConfirmDeleteDialog component                        */
/* -------------------------------------------------------------------------- */
@Component({
  selector: 'app-confirm-delete-dialog',
  templateUrl: './confirm-delete.dialog.html',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule]
})
export class ConfirmDeleteDialog {
  readonly data = inject<ConfirmDeleteData>(MAT_DIALOG_DATA);
}

/* -------------------------------------------------------------------------- */
/*                               Types & interfaces                           */
/* -------------------------------------------------------------------------- */
export interface DialogData {
  resource: string;
  dateSelectedDebut: Date;
  isJournee: boolean;
  isApresMidi: boolean;
  isMatin: boolean;
}

export interface ConfirmDeleteData {
  resource: string;
  start: Date;
  end: Date;
}

export interface EventSpan { date: DateCalendar; colspan: number; }

export class DateCalendar extends Date {

  holidays: Holiday[] = [];

  constructor(holidays: Holiday[], year: number, month: number, day: number) {
    super(year, month, day);
    this.holidays = holidays;
  }

  isHoliday(): boolean {
    let result = false;
    for (let day of this.holidays) {
      if (this.toDateString() === day.date.toDateString()) {
        result = true;
      }
    }
    return result;
  }

  isEvent(resource: Resource): boolean {
    let result = false;
    const currentDate = new Date(this.getFullYear(), this.getMonth(), this.getDate());

    for (let event of resource.events) {
      if (event.dateDebutEvent != null && event.dateFinEvent != null) {
        const start = new Date(event.dateDebutEvent);
        const end = new Date(event.dateFinEvent);

        if (currentDate >= start && currentDate <= end) {
          result = true;
        }
      }
    }
    return result;
  }

  isDemiJourneeEvent(resource: Resource): boolean {
    const currentDate = new Date(this.getFullYear(), this.getMonth(), this.getDate());

    return resource.events.some(event =>
      event.dateDebutEvent && event.dateFinEvent &&
      currentDate >= new Date(event.dateDebutEvent) &&
      currentDate <= new Date(event.dateFinEvent) &&
      (event.isApresMidi || event.isMatin)
    );
  }

  isInactif(resource: Resource): boolean {
    const today = new Date(this.getFullYear(), this.getMonth(), this.getDate());
    today.setHours(0, 0, 0, 0); // supprime les heures
    const dateDebut = new Date(resource.dateDebut);
    dateDebut.setHours(0, 0, 0, 0); // idem
    const dateFin = new Date(resource.dateFin);
    dateFin.setHours(0, 0, 0, 0); // idem

    return today < dateDebut || today > dateFin;
  }

  getEventFor(resource: Resource): Event | null {
    const currentDate = new Date(this.getFullYear(), this.getMonth(), this.getDate());

    for (let event of resource.events) {
      if (event.dateDebutEvent && event.dateFinEvent) {
        const start = new Date(event.dateDebutEvent);
        const end = new Date(event.dateFinEvent);

        if (currentDate >= start && currentDate <= end) {
          return event;
        }
      }
    }
    return null;
  }
}
