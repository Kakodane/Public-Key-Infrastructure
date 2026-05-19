import { Component, OnInit, inject, signal } from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CryptoService } from '../../core/crypto/crypto.service';
import { RegistrationService } from '../../core/registration/registration.service';
import { ActivationChallengeResponse } from '../../core/registration/registration.types';

@Component({
  selector: 'app-activate-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './activate-page.component.html',
  styleUrl: './activate-page.component.css'
})
export class ActivatePageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly registration = inject(RegistrationService);
  private readonly cryptoSvc = inject(CryptoService);

  readonly token = signal<string | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly ok = signal(false);
  readonly challenge = signal<ActivationChallengeResponse | null>(null);

  readonly form = new FormGroup({
    privateKeyPem: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    })
  });

  ngOnInit() {
    const t = this.route.snapshot.queryParamMap.get('token');
    this.token.set(t);
    if (!t) {
      this.loading.set(false);
      this.error.set('Missing activation token. Use the link from your email.');
      return;
    }
    this.registration.getActivationChallenge(t).subscribe({
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
    const pem = this.form.controls.privateKeyPem.value;
    this.busy.set(true);
    let decrypted: string;
    try {
      const priv = await this.cryptoSvc.importPrivateKeyPem(pem);
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
    this.registration.activate({ token: t, decryptedChallengeBase64: decrypted }).subscribe({
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

  get keyFingerprint(): string {
    const pem = this.challenge()?.publicKeyPem ?? '';
    const stripped = pem.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
    return stripped.length > 16 ? '…' + stripped.slice(-16) : stripped;
  }
}

function extractError(err: any): string {
  if (err?.error?.errors?.length) return err.error.errors.join(', ');
  if (err?.status === 410) return err?.error?.message ?? 'This activation link has expired.';
  if (err?.status === 400) return err?.error?.message ?? 'Invalid token or challenge.';
  if (err?.status === 404) return err?.error?.message ?? 'Token not found.';
  if (err?.status === 0) return 'Cannot reach the server. Check your connection.';
  return err?.error?.message ?? err?.message ?? 'Activation failed.';
}