import { Injectable } from '@angular/core';
import { Holiday } from '../model/Resource';


@Injectable({
  providedIn: 'root',
})
export class HolidayService {
  private holidays: Holiday[] = [];

  constructor() {
    console.log("constructor HolidayService appelé");
  }

  private calculateHolidays(year: number) {
    this.holidays.push(new Holiday(new Date(year, 0, 1), 'Jour de l\'An'));
    this.holidays.push(new Holiday(HolidayService.getPaquesDay(year), 'Lundi de Pâques'  ));
    this.holidays.push(new Holiday(new Date(year, 4, 1), 'Fête du Travail'  ));
    this.holidays.push(new Holiday(new Date(year, 4, 8), 'Victoire 1945'  ));
    this.holidays.push(new Holiday(HolidayService.getAscensionDate(year), 'Ascension'  ));
    this.holidays.push(new Holiday(HolidayService.getPentecostMonday(year), 'Lundi de Pentecôte'  ));
    this.holidays.push(new Holiday(new Date(year, 6, 14), 'Fête Nationale'  ));
    this.holidays.push(new Holiday(new Date(year, 7, 15), 'Assomption'));
    this.holidays.push(new Holiday(new Date(year, 10, 1), 'Toussaint'));
    this.holidays.push(new Holiday(new Date(year, 10, 11), 'Armistice 1918'));
    this.holidays.push(new Holiday(new Date(year, 11, 25), 'Noël'));
  }

  public static getPaquesDay(annee: number): Date {
    let  datePaques = new Date();
    switch ( annee ) {
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


  getHolidays(annee : number) {
    this.calculateHolidays(annee);
    return this.holidays;
  }
}
