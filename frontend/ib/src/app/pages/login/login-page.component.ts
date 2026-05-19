import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
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

  get emailErrors() {
    const c = this.form.controls.email;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'Email is required.';
    if (c.hasError('email')) return 'Enter a valid email address (e.g. user@example.com).';
    return null;
  }

  get passwordErrors() {
    const c = this.form.controls.password;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'Password is required.';
    return null;
  }

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
        this.error.set(extractLoginError(err));
        this.busy.set(false);
      },
      complete: () => this.busy.set(false)
    });
  }
}

function extractLoginError(err: any): string {
  const msg = err?.error?.message;
  if (typeof msg === 'string' && msg.trim()) return msg;
  const status = err?.status;
  if (status === 401) return 'Invalid email or password.';
  if (status === 403) return 'Account is disabled or email not yet verified.';
  if (status === 0) return 'Cannot reach the server. Check your connection.';
  return 'Login failed. Please try again.';
}

