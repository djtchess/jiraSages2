import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { RouterModule } from '@angular/router';
import { OverlayContainer } from '@angular/cdk/overlay';
import { ThemeService } from './theme.service';

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
    RouterModule,
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class AppComponent implements OnInit {
  darkMode = false;

  constructor(
    private overlayContainer: OverlayContainer,
    public themeService: ThemeService,
  ) {}

  ngOnInit() {
    this.themeService.initTheme();
    this.themeService.activeTheme$.subscribe((theme) => {
      this.darkMode = theme === 'dark';
      this.syncOverlayTheme(this.darkMode);
    });
  }

  toggleDarkMode() {
    this.themeService.toggleTheme();
  }

  private syncOverlayTheme(isDark: boolean): void {
    const overlayContainerClasses = this.overlayContainer.getContainerElement().classList;
    overlayContainerClasses.toggle('dark-mode', isDark);
  }
}
