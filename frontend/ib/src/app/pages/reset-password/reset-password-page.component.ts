import { Component, OnInit, inject, signal } from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CryptoService } from '../../core/crypto/crypto.service';
import { PasswordResetService } from '../../core/password-reset/password-reset.service';
import { PasswordResetChallengeResponse } from '../../core/password-reset/password-reset.types';
import { passwordsMatch } from '../../core/auth/passwords-match.validator';

@Component({
  selector: 'app-reset-password-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password-page.component.html',
  styleUrl: './reset-password-page.component.css'
})
export class ResetPasswordPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly passwordReset = inject(PasswordResetService);
  private readonly cryptoSvc = inject(CryptoService);

  readonly token = signal<string | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly ok = signal(false);
  readonly challenge = signal<PasswordResetChallengeResponse | null>(null);

  readonly form = new FormGroup(
    {
      privateKeyPem: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required]
      }),
      newPassword: new FormControl('', {
        nonNullable: true,
        validators: [
          Validators.required,
          Validators.minLength(8),
          Validators.maxLength(128),
          Validators.pattern(
            /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/
          )
        ]
      }),
      confirmPassword: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required]
      })
    },
    { validators: passwordsMatch('newPassword', 'confirmPassword') }
  );

  ngOnInit() {
    const t = this.route.snapshot.queryParamMap.get('token');
    this.token.set(t);
    if (!t) {
      this.loading.set(false);
      this.error.set('Missing reset token. Use the link from your email.');
      return;
    }
    this.passwordReset.getResetChallenge(t).subscribe({
      next: (c) => {
        this.challenge.set(c);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(extractError(err));
      }
    });
  }

  onFileSelected(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    file.text().then(
      (text) => this.form.controls.privateKeyPem.setValue(text),
      () => this.error.set('Failed to read file.')
    );
  }

  async submit() {
    this.error.set(null);
    const t = this.token();
    const c = this.challenge();
    if (!t || !c) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { privateKeyPem, newPassword, confirmPassword } = this.form.getRawValue();
    this.busy.set(true);
    let decrypted: string;
    try {
         const priv = await this.cryptoSvc.importPrivateKeyPem(privateKeyPem);
      try {
        decrypted = await this.cryptoSvc.decryptChallenge(priv, c.encryptedChallengeBase64);
      }
      catch {
        this.busy.set(false);
        this.error.set("That private key doesn't match the public key on file.");
        return;
      }
    }
    catch {
      this.busy.set(false);
      this.error.set("That doesn't look like a valid RSA private key in PEM format.");
      return;
    }
    this.passwordReset.completeReset({
      token: t,
      decryptedChallengeBase64: decrypted,
      newPassword,
      confirmPassword
    }).subscribe({
      next: () => {
        this.ok.set(true);
        this.busy.set(false);
        setTimeout(() => this.router.navigateByUrl('/login'), 1500);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(extractError(err));
      }
    });
  }

  get passwordErrors() {
    const c = this.form.controls.newPassword;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'Password is required.';
    if (c.hasError('minlength') || c.hasError('maxlength') || c.hasError('pattern')) {
      return 'Password does not meet the strength requirements.';
    }
    return null;
  }

  get keyFingerprint(): string {
    const pem = this.challenge()?.publicKeyPem ?? '';
    const stripped = pem.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
    return stripped.length > 16 ? '…' + stripped.slice(-16) : stripped;
  }
}

function extractError(err: any): string {
  if (err?.error?.errors?.length) return err.error.errors.join(', ');
  if (err?.status === 410) return err?.error?.message ?? 'This reset link has expired.';
  if (err?.status === 400) return err?.error?.message ?? 'Invalid token or challenge.';
  if (err?.status === 404) return err?.error?.message ?? 'Token not found.';
  if (err?.status === 0) return 'Cannot reach the server. Check your connection.';
  return err?.error?.message ?? err?.message ?? 'Password reset failed.';
}