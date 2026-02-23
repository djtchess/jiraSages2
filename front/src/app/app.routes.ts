import { Routes } from '@angular/router';
import { BurnupChartComponent } from './burnup-chart/burnup-chart.component';
import { CalendarComponent } from './calendar/calendar.component';
import { EpicDurationChartComponent } from './epic-duration-chart/epic-duration-chart.component';
import { SprintCapacityComponent } from './sprint-capacity/sprint-capacity.component';


export const routes: Routes = [
  // { path: '', loadComponent: () => import('./home/home.component').then(m => m.HomeComponent)},
   { path: 'sprints', loadComponent: () => import('./sprint-list/sprint-list.component').then(m => m.SprintListComponent)   },
    { path: 'boards/:boardId/sprints/:sprintId/capacity', component: SprintCapacityComponent },
   { path: 'sprint/:id/:name/burnup', component: BurnupChartComponent },
   { path: 'calendar', component: CalendarComponent },
   { path: 'epic-durations', component: EpicDurationChartComponent }
];
