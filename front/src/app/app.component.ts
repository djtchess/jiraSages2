import { Component, ViewEncapsulation } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';

import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { RouterModule } from '@angular/router';
import { OverlayContainer } from '@angular/cdk/overlay';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    MatSidenavModule,
    MatToolbarModule,
    MatIconModule,
    MatListModule,
    MatButtonModule,
    MatExpansionModule,
    RouterModule
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],  // ou .scss si tu utilises scss global
  encapsulation: ViewEncapsulation.None
})
export class AppComponent {
  title = 'sages2-scrum';

  menuItems = [
    { label: 'Accueil', route: '/', icon: 'home' },
    { label: 'Liste des sprints', route: '/sprints', icon: 'folder' },
    { label: 'Planning', route: '/calendar', icon: 'settings' },
  ];

  darkMode = false;

  constructor(private overlayContainer: OverlayContainer) {}

  ngOnInit() {
    this.setDarkMode(this.darkMode);
  }

  toggleDarkMode() {
    this.darkMode = !this.darkMode;
    this.setDarkMode(this.darkMode);
  }

  private setDarkMode(enabled: boolean) {
    const overlayContainerClasses = this.overlayContainer.getContainerElement().classList;
    if (enabled) {
      overlayContainerClasses.add('dark-mode');
      document.body.classList.add('dark-mode');
    } else {
      overlayContainerClasses.remove('dark-mode');
      document.body.classList.remove('dark-mode');
    }
  }
}
