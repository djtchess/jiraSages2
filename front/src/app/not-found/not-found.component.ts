import { Component } from '@angular/core';

@Component({
  selector: 'app-not-found',
  standalone: true,
  template: `
    <section class="not-found">
      <h1>404</h1>
      <p>Page introuvable</p>
    </section>
  `
})
export class NotFoundComponent {}
