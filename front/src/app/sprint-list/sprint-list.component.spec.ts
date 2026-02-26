import { of } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SprintListComponent } from './sprint-list.component';
import { JiraService } from '../../service/jira.service';

describe('SprintListComponent', () => {
  let component: SprintListComponent;
  let fixture: ComponentFixture<SprintListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SprintListComponent],
      providers: [
        {
          provide: JiraService,
          useValue: {
            getSprintsForProject: () => of([])
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SprintListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
