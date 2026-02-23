
import { Ticket } from '../../model/SprintInfo.model';
import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { MatSort } from '@angular/material/sort';

import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatSortModule } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-ticket-table',
  standalone: true,
  imports: [    
    CommonModule,
    MatTableModule,
    MatIconModule,
    MatSortModule,
    MatFormFieldModule,
    MatInputModule
  ],
  templateUrl: './ticket-table.component.html',
  styleUrl: './ticket-table.component.css'
})
export class TicketTableComponent implements OnInit {
  @Input() tickets: Ticket[] = [];

  displayedColumns: string[] = ['ticketKey', 'assignee', 'status', 'storyPoints','storyPointsRealiseAvantSprint', 'type', 'versionCorrigee', 'done'];
  dataSource = new MatTableDataSource<Ticket>();

  @ViewChild(MatSort) sort!: MatSort;

  ngOnInit(): void {
    this.dataSource.data = this.tickets;
  }

  ngAfterViewInit(): void {
    this.dataSource.sort = this.sort;
  }

  applyFilter(event: Event): void {
    const filterValue = (event.target as HTMLInputElement).value.trim().toLowerCase();
    this.dataSource.filter = filterValue;
  }

  getTypeIcon(type: string): string {
    switch (type.toLowerCase()) {
      case 'bug': return 'bug_report';
      case 'story': return 'menu_book';
      case 'task': return 'check_circle';
      default: return 'label';
    }
  }

  getTypeColor(type: string): 'primary' | 'accent' | 'warn' | undefined {
  switch (type.toLowerCase()) {
    case 'bug': return 'warn';
    case 'story': return 'primary';
    case 'task': return 'accent';
    default: return undefined;
  }
}


  getStatusIcon(status: string): string {
    if (!status) return 'hourglass_empty';
    const s = status.toLowerCase();
    if (s.includes('done') || s === 'terminé') return 'check';
    if (s.includes('progress')) return 'autorenew';
    if (s.includes('todo')) return 'schedule';
    return 'hourglass_empty';
  }

  isTicketDone(ticket: Ticket): boolean {
  const doneStatuses = [
    'TACHE TECHNIQUE TESTEE', 'DEV TERMINE', 'FAIT',
    'LIVRÉ À TESTER', 'NON TESTABLE', 'RESOLU',
    'TESTÉ', 'TESTS UTR', 'TERMINE', 'INTEGRATION PR', 'READY TO DEMO'
  ];

  return ticket.status != null &&
         doneStatuses.includes(ticket.status.toUpperCase());
}
  
}
