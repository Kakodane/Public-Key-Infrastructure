import { Component, computed, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { QRCodeComponent } from 'angularx-qrcode';
import { AuthService } from '../../core/auth/auth.service';
import { isRegularUser } from '../../core/auth/role';
import { TokenStorageService } from '../../core/auth/token-storage.service';

type TwoFactorUiStep = 'idle' | 'setup' | 'disable';

@Component({
  selector: 'app-me-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, QRCodeComponent],
  templateUrl: './me-page.component.html'
})
export class MePageComponent {
  readonly twoFactorStep = signal<TwoFactorUiStep>('idle');
  readonly twoFactorBusy = signal(false);
  readonly twoFactorError = signal<string | null>(null);
  readonly qrCodeUri = signal<string | null>(null);
  readonly setupSecret = signal<string | null>(null);

  readonly confirmForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)]
    })
  });

  readonly disableForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)]
    })
  });

  readonly showTwoFactorSection = computed(() => {
    const me = this.auth.me();
    return me != null && isRegularUser(me.role);
  });

  constructor(
    readonly auth: AuthService,
    readonly tokenStorage: TokenStorageService,
    private readonly router: Router
  ) {}

  get confirmCodeErrors() {
    const c = this.confirmForm.controls.code;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'Code is required.';
    if (c.hasError('pattern')) return 'Enter the 6-digit code from your authenticator app.';
    return null;
  }

  get disableCodeErrors() {
    const c = this.disableForm.controls.code;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'Code is required.';
    if (c.hasError('pattern')) return 'Enter the 6-digit code from your authenticator app.';
    return null;
  }

  logout() {
    this.auth.logout().subscribe({
      complete: () => this.router.navigateByUrl('/login')
    });
  }

  startSetup() {
    this.twoFactorError.set(null);
    this.twoFactorBusy.set(true);
    this.auth.setup2fa().subscribe({
      next: (res) => {
        this.qrCodeUri.set(res.qrCodeUri);
        this.setupSecret.set(res.secret);
        this.confirmForm.reset();
        this.twoFactorStep.set('setup');
        this.twoFactorBusy.set(false);
      },
      error: (err) => {
        this.twoFactorError.set(extractTwoFactorError(err, 'Failed to start 2FA setup.'));
        this.twoFactorBusy.set(false);
      }
    });
  }

  confirmSetup() {
    this.twoFactorError.set(null);
    if (this.confirmForm.invalid) {
      this.confirmForm.markAllAsTouched();
      return;
    }
    const { code } = this.confirmForm.getRawValue();
    this.twoFactorBusy.set(true);
    this.auth.confirm2fa(code).subscribe({
      next: () => this.resetTwoFactorUi(),
      error: (err) => {
        this.twoFactorError.set(extractTwoFactorError(err, 'Invalid code. Try again.'));
        this.twoFactorBusy.set(false);
      },
      complete: () => this.twoFactorBusy.set(false)
    });
  }

  startDisable() {
    this.twoFactorError.set(null);
    this.disableForm.reset();
    this.twoFactorStep.set('disable');
  }

  confirmDisable() {
    this.twoFactorError.set(null);
    if (this.disableForm.invalid) {
      this.disableForm.markAllAsTouched();
      return;
    }
    const { code } = this.disableForm.getRawValue();
    this.twoFactorBusy.set(true);
    this.auth.disable2fa(code).subscribe({
      next: () => this.resetTwoFactorUi(),
      error: (err) => {
        this.twoFactorError.set(extractTwoFactorError(err, 'Invalid code. Try again.'));
        this.twoFactorBusy.set(false);
      },
      complete: () => this.twoFactorBusy.set(false)
    });
  }

  cancelTwoFactorAction() {
    this.resetTwoFactorUi();
  }

  private resetTwoFactorUi() {
    this.twoFactorStep.set('idle');
    this.qrCodeUri.set(null);
    this.setupSecret.set(null);
    this.confirmForm.reset();
    this.disableForm.reset();
    this.twoFactorError.set(null);
    this.twoFactorBusy.set(false);
  }
}

function extractTwoFactorError(err: any, fallback: string): string {
  const msg = err?.error?.message;
  if (typeof msg === 'string' && msg.trim()) return msg;
  if (err?.status === 401) return 'Invalid authentication code.';
  if (err?.status === 0) return 'Cannot reach the server. Check your connection.';
  return fallback;
}
