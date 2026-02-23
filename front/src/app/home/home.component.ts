import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h1>Bienvenue sur la page d'accueil !</h1>
    <p>Ceci est un composant standalone.</p>
  `,
  styleUrls: ['./home.component.css']
})
export class HomeComponent {}
