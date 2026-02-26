import { OverlayContainer } from '@angular/cdk/overlay';
import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterModule } from '@angular/router';
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
    RouterModule
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
  encapsulation: ViewEncapsulation.None
})
export class AppComponent implements OnInit {
  darkMode = false;
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private readonly overlayContainer: OverlayContainer,
    public readonly themeService: ThemeService
  ) {}

  ngOnInit(): void {
    this.themeService.initTheme();
    this.themeService.activeTheme$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((theme) => {
        this.darkMode = theme === 'dark';
        this.syncOverlayTheme(this.darkMode);
      });
  }

  toggleDarkMode(): void {
    this.themeService.toggleTheme();
  }

  private syncOverlayTheme(isDark: boolean): void {
    const overlayContainerClasses = this.overlayContainer.getContainerElement().classList;
    overlayContainerClasses.toggle('dark-mode', isDark);
  }
}
