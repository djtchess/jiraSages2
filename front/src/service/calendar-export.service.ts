import { Injectable } from '@angular/core';
import html2pdf from 'html2pdf.js';

@Injectable({ providedIn: 'root' })
export class CalendarExportService {
  exportCalendarAsPdf(element: HTMLElement, year: number, month: number): Promise<void> {
    element.classList.add('pdf-export');

    return html2pdf()
      .from(element)
      .set({
        margin: 5,
        filename: `calendrier_${year}_${month}.pdf`,
        image: { type: 'jpeg', quality: 0.98 },
        html2canvas: { scale: 2 },
        jsPDF: { unit: 'mm', format: 'a4', orientation: 'landscape' }
      })
      .save()
      .finally(() => element.classList.remove('pdf-export'));
  }
}
