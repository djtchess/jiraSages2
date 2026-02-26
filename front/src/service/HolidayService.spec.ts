import { TestBed } from '@angular/core/testing';

import { HolidayService } from './HolidayService';

describe('HolidayService', () => {
  let service: HolidayService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(HolidayService);
  });

  it('should not accumulate holidays for repeated calls on same year', () => {
    const firstCall = service.getHolidays(2025);
    const secondCall = service.getHolidays(2025);

    expect(firstCall.length).toBe(11);
    expect(secondCall.length).toBe(11);
  });

  it('should return a new array instance on each call', () => {
    const firstCall = service.getHolidays(2025);
    const secondCall = service.getHolidays(2025);

    expect(firstCall).not.toBe(secondCall);
  });
});
