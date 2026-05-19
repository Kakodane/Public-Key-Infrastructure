import { Component, inject, signal } from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PasswordResetService } from '../../core/password-reset/password-reset.service';

@Component({
  selector: 'app-forgot-password-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password-page.component.html',
  styleUrl: './forgot-password-page.component.css'
})
export class ForgotPasswordPageComponent {
  private readonly passwordReset = inject(PasswordResetService);

  readonly busy = signal(false);
  readonly submitted = signal(false);

  readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email]
    })
  });

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const email = this.form.controls.email.value.trim();
    this.busy.set(true);
    this.passwordReset.requestReset({ email }).subscribe({
      next: () => {
        this.busy.set(false);
        this.submitted.set(true);
      },
      error: () => {
        this.busy.set(false);
        this.submitted.set(true);
      }
    });
  }

  get emailErrors() {
    const c = this.form.controls.email;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'Email is required.';
    if (c.hasError('email')) return 'Enter a valid email address.';
    return null;
  }
}