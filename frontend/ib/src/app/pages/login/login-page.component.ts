import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login-page.component.html'
})
export class LoginPageComponent {
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router
  ) {}

  submit() {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.busy.set(true);
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl('/me'),
      error: (err) => {
        this.error.set(err?.error?.message ?? err?.error ?? 'Login failed');
        this.busy.set(false);
      },
      complete: () => this.busy.set(false)
    });
  }
}

