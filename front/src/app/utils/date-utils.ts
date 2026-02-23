export class DateUtils {
  static isWeekend(date: Date): boolean {
    const day = date.getDay();
    return day === 0 || day === 6;
  }

  static isSameDay(d1: Date, d2: Date): boolean {
    return d1.getDate() === d2.getDate() &&
           d1.getMonth() === d2.getMonth() &&
           d1.getFullYear() === d2.getFullYear();
  }

  static isToday(date: Date): boolean {
    return this.isSameDay(date, new Date());
  }

  static getLastDayOfMonth(date: Date): number {
    return new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
  }

  // Ajoute ici d'autres helpers si nécessaire
}
