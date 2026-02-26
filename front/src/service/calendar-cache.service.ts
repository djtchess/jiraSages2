import { Injectable } from '@angular/core';
import { Resource } from '../model/Resource';
import { DateUtils } from '../app/utils/date-utils';
import { DateCalendar, EventSpan } from '../app/calendar/calendar.component';

@Injectable({ providedIn: 'root' })
export class CalendarCacheService {
  private readonly maxCacheMonths = 6;
  private readonly spanCache = new Map<string, Map<string, EventSpan[]>>();
  private readonly presenceDaysCache = new Map<string, Map<number, number>>();

  getSpans(key: string, resourceName: string): EventSpan[] {
    return this.spanCache.get(key)?.get(resourceName) ?? [];
  }

  ensureMonthSpans(
    key: string,
    dates: DateCalendar[],
    activeResources: Resource[],
    buildSpansForMonth: (dates: DateCalendar[], resources: Resource[]) => Map<string, EventSpan[]>
  ): void {
    if (this.spanCache.has(key)) {
      return;
    }

    this.spanCache.set(key, buildSpansForMonth(dates, activeResources));
    this.trimSpanCache();
  }

  setMonthSpans(key: string, spans: Map<string, EventSpan[]>): void {
    this.spanCache.set(key, spans);
    this.trimSpanCache();
  }

  invalidateMonth(key: string): void {
    this.spanCache.delete(key);
  }

  computePresence(displayedMonthKeys: string[], activeResources: Resource[]): void {
    this.presenceDaysCache.clear();

    for (const res of activeResources) {
      const monthMap = new Map<number, number>();

      displayedMonthKeys.forEach((key, displayIdx) => {
        const spans = this.spanCache.get(key)?.get(res.prenomResource) ?? [];
        let total = 0;

        spans.forEach((span) => {
          const date = span.date;
          const notWorkingDay =
            DateUtils.isWeekend(date) ||
            date.isHoliday() ||
            date.isInactif(res) ||
            (date.isEvent(res) && !date.isDemiJourneeEvent(res));

          if (!notWorkingDay) {
            total += date.isDemiJourneeEvent(res) ? 0.5 : 1;
          }
        });

        monthMap.set(displayIdx, total);
      });

      this.presenceDaysCache.set(res.prenomResource, monthMap);
    }
  }

  getPresenceDays(resourceName: string, monthIndex: number): number {
    return this.presenceDaysCache.get(resourceName)?.get(monthIndex) ?? 0;
  }

  private trimSpanCache(): void {
    while (this.spanCache.size > this.maxCacheMonths) {
      const oldestKey = [...this.spanCache.keys()].sort((a, b) => {
        const [yA, mA] = a.split('-').map(Number);
        const [yB, mB] = b.split('-').map(Number);
        return yA !== yB ? yA - yB : mA - mB;
      })[0];

      this.spanCache.delete(oldestKey);
    }
  }
}
