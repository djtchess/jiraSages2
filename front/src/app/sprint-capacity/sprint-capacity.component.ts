import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { SprintCapacityService } from '../../service/sprint-capacity.service';
import { CapaciteDevProchainSprintDTO } from '../../model/capacite-dev-prochain-sprint.model';

// Optionnel si tu utilises Angular Material :
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-sprint-capacity',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatChipsModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './sprint-capacity.component.html',
  styleUrls: ['./sprint-capacity.component.css']
})
export class SprintCapacityComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private capacityService = inject(SprintCapacityService);

  boardId!: number;
  sprintId!: number;

  isLoading = true;
  data: CapaciteDevProchainSprintDTO[] = [];
  displayedColumns = [
    'prenom',
    'capaciteNouveauSprint',
    'resteAFaireSprintPrecedent',
    'capaciteNette',
    'ticketsRestantsKeys'
  ];

  ngOnInit(): void {
    console.log("SprintCapacityComponent init");
    // Route: /boards/:boardId/sprints/:sprintId/capacity
    this.boardId = Number(this.route.snapshot.paramMap.get('boardId'));
    this.sprintId = Number(this.route.snapshot.paramMap.get('sprintId'));

    this.capacityService.getAllDevCapacityAndCarryover(this.boardId, this.sprintId).subscribe({
      next: res => { this.data = res; this.isLoading = false; },
      error: _ => { this.isLoading = false; }
    });
  }

  back(): void {
    this.router.navigate(['/sprints']); // adapte le chemin retour si besoin
  }
}
