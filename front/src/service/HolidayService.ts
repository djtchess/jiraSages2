import { Injectable } from '@angular/core';
import { Holiday } from '../model/Resource';

@Injectable({
  providedIn: 'root',
})
export class HolidayService {
  private readonly holidaysByYear = new Map<number, readonly Holiday[]>();

  private calculateHolidays(year: number): Holiday[] {
    return [
      new Holiday(new Date(year, 0, 1), "Jour de l'An"),
      new Holiday(HolidayService.getPaquesDay(year), 'Lundi de Pâques'),
      new Holiday(new Date(year, 4, 1), 'Fête du Travail'),
      new Holiday(new Date(year, 4, 8), 'Victoire 1945'),
      new Holiday(HolidayService.getAscensionDate(year), 'Ascension'),
      new Holiday(HolidayService.getPentecostMonday(year), 'Lundi de Pentecôte'),
      new Holiday(new Date(year, 6, 14), 'Fête Nationale'),
      new Holiday(new Date(year, 7, 15), 'Assomption'),
      new Holiday(new Date(year, 10, 1), 'Toussaint'),
      new Holiday(new Date(year, 10, 11), 'Armistice 1918'),
      new Holiday(new Date(year, 11, 25), 'Noël')
    ];
  }

  public static getPaquesDay(annee: number): Date {
    let datePaques = new Date();
    switch (annee) {
      case 2023:
        // statement 1
        break;
      case 2024:
        datePaques = new Date(annee, 3, 1);
        break;
      case 2025:
        datePaques = new Date(annee, 3, 21);
        break;
      case 2026:
        datePaques = new Date(annee, 3, 6);
        break;
      case 2027:
        datePaques = new Date(annee, 2, 29);
        break;
      default:
        //
        break;
    }
    return datePaques;
  }

  public static getAscensionDate(year: number): Date {
    const easterDate = this.getPaquesDay(year);
    const ascensionDate = new Date(easterDate);
    ascensionDate.setDate(ascensionDate.getDate() + 38); // L'Ascension est 40 jours après Pâques (dimanche), donc +39
    return ascensionDate;
  }

  public static getPentecostMonday(year: number): Date {
    const easterDate = this.getPaquesDay(year);
    const pentecostMonday = new Date(easterDate);
    pentecostMonday.setDate(pentecostMonday.getDate() + 49);
    return pentecostMonday;
  }

  getHolidays(annee: number): Holiday[] {
    if (!this.holidaysByYear.has(annee)) {
      const holidays = this.calculateHolidays(annee);
      this.holidaysByYear.set(annee, Object.freeze(holidays));
    }

    return this.cloneHolidays(this.holidaysByYear.get(annee) ?? []);
  }

  private cloneHolidays(holidays: readonly Holiday[]): Holiday[] {
    return holidays.map((holiday) => new Holiday(new Date(holiday.date), holiday.name));
  }
}
