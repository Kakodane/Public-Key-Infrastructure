import { Component, inject, signal } from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CryptoService } from '../../core/crypto/crypto.service';
import { passwordsMatch } from '../../core/auth/passwords-match.validator';
import { RegistrationService } from '../../core/registration/registration.service';

type KeyMode = 'generate' | 'paste';

@Component({
  selector: 'app-register-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register-page.component.html',
  styleUrl: './register-page.component.css'
})
export class RegisterPageComponent {
  private readonly cryptoSvc = inject(CryptoService);
  private readonly registration = inject(RegistrationService);

  readonly busy = signal(false);
  readonly generating = signal(false);
  readonly error = signal<string | null>(null);
  readonly ok = signal(false);
  readonly keyMode = signal<KeyMode>('generate');
  readonly downloadedHint = signal<string | null>(null);

  readonly form = new FormGroup(
    {
      email: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.email]
      }),
      firstName: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required]
      }),
      lastName: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required]
      }),
      organizationName: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required]
      }),
      password: new FormControl('', {
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
      }),
      publicKeyPem: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required]
      })
    },
    { validators: passwordsMatch('password', 'confirmPassword') }
  );

  setMode(m: KeyMode) {
    this.keyMode.set(m);
    this.form.controls.publicKeyPem.setValue('');
    this.downloadedHint.set(null);
    if (m === 'paste') {
      this.form.controls.publicKeyPem.enable();
    }
    else {
      this.form.controls.publicKeyPem.disable();
    }
  }

  async generateAndDownload() {
    this.error.set(null);
    if (this.busy() || this.generating()) return;
    const email = this.form.controls.email.value.trim() || 'user';
    this.generating.set(true);
    try {
      const pair = await this.cryptoSvc.generateKeyPair();
      const [pubPem, privPem] = await Promise.all([
        this.cryptoSvc.exportPublicKeyPem(pair.publicKey),
        this.cryptoSvc.exportPrivateKeyPem(pair.privateKey)
      ]);
      this.form.controls.publicKeyPem.setValue(pubPem);
      triggerDownload(privPem, `ib-private-key-${sanitize(email)}.pem`);
      this.downloadedHint.set(`Saved as ib-private-key-${sanitize(email)}.pem`);
    }
     catch (e: any) {
      this.error.set('Failed to generate keypair: ' + (e?.message ?? 'unknown error'));
    }
    finally {
      this.generating.set(false);
    }
  }

  submit() {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      if (this.form.errors?.['mismatch']) {
      }
      return;
    }
    const pubKey = this.form.controls.publicKeyPem.value?.trim();
    if (!pubKey) {
      this.error.set('A public key is required. Generate one or paste your own.');
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.registration.register({
      email: v.email.trim(),
      password: v.password,
      confirmPassword: v.confirmPassword,
      firstName: v.firstName.trim(),
      lastName: v.lastName.trim(),
      organizationName: v.organizationName.trim(),
      publicKeyPem: pubKey
    }).subscribe({
      next: () => {
        this.ok.set(true);
        this.busy.set(false);
      },
      error: (err) => {
        this.error.set(extractError(err));
        this.busy.set(false);
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
  get passwordErrors() {
    const c = this.form.controls.password;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'Password is required.';
    if (c.hasError('minlength') || c.hasError('maxlength') || c.hasError('pattern')) {
      return 'Password does not meet the strength requirements.';
    }
    return null;
  }
  get requiredError() {
    return 'This field is required.';
  }
}

function triggerDownload(content: string, filename: string) {
  const blob = new Blob([content], { type: 'application/x-pem-file' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

function sanitize(s: string): string {
  return s.replace(/[^a-zA-Z0-9._-]/g, '_');
}

function extractError(err: any): string {
  if (err?.error?.errors?.length) {
    return err.error.errors.join(', ');
  }
  if (err?.status === 409) {
    return err?.error?.message ?? 'An account with this email already exists.';
  }
  if (err?.status === 404) {
    return err?.error?.message ?? 'Organization not found. Ask an admin to create it first.';
  }
  if (err?.status === 400) {
    return err?.error?.message ?? 'The submitted data was rejected.';
  }
  if (err?.status === 0) {
    return 'Cannot reach the server. Check your connection.';
  }
  return err?.error?.message ?? err?.message ?? 'Registration failed.';
}