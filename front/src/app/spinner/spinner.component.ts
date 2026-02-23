import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-spinner',
  standalone: true,
  imports: [],
  templateUrl: './spinner.component.html',
  styleUrl: './spinner.component.css'
})
export class SpinnerComponent implements OnInit {
  isLoading = false;
  data: any;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.isLoading = true;
    this.http.get('https://api.exemple.com/donnees')
      .pipe(
        finalize(() => this.isLoading = false)
      )
      .subscribe({
        next: res => this.data = res,
        error: err => console.error('Erreur de chargement', err)
      });
  }
}
