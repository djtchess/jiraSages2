import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BurnupChartComponent } from './burnup-chart.component';

describe('BurnupChartComponent', () => {
  let component: BurnupChartComponent;
  let fixture: ComponentFixture<BurnupChartComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BurnupChartComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(BurnupChartComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
