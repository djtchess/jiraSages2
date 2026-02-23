// confirm-delete.dialog.ts
import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';

export interface ConfirmDeleteData {
  resource: string;
  start: Date;
  end: Date;
}

@Component({
  selector: 'app-confirm-delete-dialog',
  standalone: true,
  templateUrl: './confirm-delete.dialog.html',
  imports: [CommonModule, MatDialogModule, MatButtonModule]
})
export class ConfirmDeleteDialog {
  readonly data = inject<ConfirmDeleteData>(MAT_DIALOG_DATA);
}
