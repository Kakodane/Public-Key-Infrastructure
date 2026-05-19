import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

type LoginStep = 'credentials' | 'totp';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login-page.component.html'
})
export class LoginPageComponent {
  readonly step = signal<LoginStep>('credentials');
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly twoFactorToken = signal<string | null>(null);

  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  readonly totpForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)]
    })
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

  get totpErrors() {
    const c = this.totpForm.controls.code;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'Authentication code is required.';
    if (c.hasError('pattern')) return 'Enter the 6-digit code from your authenticator app.';
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
      next: (result) => {
        if (result.twoFactorRequired) {
          this.twoFactorToken.set(result.twoFactorToken);
          this.step.set('totp');
          this.totpForm.reset();
          this.busy.set(false);
          return;
        }
        this.router.navigateByUrl('/me');
      },
      error: (err) => {
        this.error.set(extractLoginError(err));
        this.busy.set(false);
      },
      complete: () => this.busy.set(false)
    });
  }

  submitTotp() {
    this.error.set(null);
    if (this.totpForm.invalid) {
      this.totpForm.markAllAsTouched();
      return;
    }
    const token = this.twoFactorToken();
    if (!token) {
      this.error.set('Session expired. Please sign in again.');
      this.backToCredentials();
      return;
    }
    const { code } = this.totpForm.getRawValue();
    this.busy.set(true);
    this.auth.verifyTwoFactor(token, code).subscribe({
      next: () => this.router.navigateByUrl('/me'),
      error: (err) => {
        this.error.set(extractTotpError(err));
        this.busy.set(false);
      },
      complete: () => this.busy.set(false)
    });
  }

  backToCredentials() {
    this.step.set('credentials');
    this.twoFactorToken.set(null);
    this.totpForm.reset();
    this.error.set(null);
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

function extractTotpError(err: any): string {
  const msg = err?.error?.message;
  if (typeof msg === 'string' && msg.trim()) return msg;
  const status = err?.status;
  if (status === 401) return 'Invalid or expired authentication code.';
  if (status === 0) return 'Cannot reach the server. Check your connection.';
  return 'Verification failed. Please try again.';
}
