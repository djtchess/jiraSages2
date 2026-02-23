import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SprintScopeComponent } from './sprint-scope.component';

describe('SprintScopeComponent', () => {
  let component: SprintScopeComponent;
  let fixture: ComponentFixture<SprintScopeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SprintScopeComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SprintScopeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
