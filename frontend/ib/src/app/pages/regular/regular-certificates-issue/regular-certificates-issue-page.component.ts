import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { Router } from '@angular/router';
import { GetCertificateResponse } from '../../../core/admin/admin.types';
import { CertificateDownloadService } from '../../../core/certificate/certificate-download.service';
import { CertificateIssueService } from '../../../core/certificate/certificate-issue.service';
import {
  AutogenerateEeCertificateRequest,
  AutogenerateEeCertificateResponse,
  CreateCsrCertificateRequest,
  GetIssuerCertificateResponse
} from '../../../core/certificate/certificate-issue.types';

function countryValidator(control: AbstractControl): ValidationErrors | null {
  const v = (control.value ?? '').trim();
  if (v === '') return null;
  return v.length === 2 ? null : { length: true };
}

function toIso(dateOnly: string): string {
  return new Date(dateOnly + 'T00:00:00Z').toISOString();
}

function toDateOnly(iso: string): string {
  return new Date(iso).toISOString().slice(0, 10);
}

function extractError(err: unknown): string {
  const e = err as any;
  if (e?.error?.errors?.length) return (e.error.errors as string[]).join(', ');
  return e?.error?.message ?? e?.message ?? 'Request failed';
}

@Component({
  selector: 'app-regular-certificates-issue-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './regular-certificates-issue-page.component.html',
  styleUrl: './regular-certificates-issue-page.component.css'
})
export class RegularCertificatesIssuePageComponent implements OnInit {
  private readonly issueService = inject(CertificateIssueService);
  private readonly certDownload = inject(CertificateDownloadService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly activeTab = signal<'csr' | 'autogenerate'>('csr');
  readonly issuers = signal<GetIssuerCertificateResponse[]>([]);

  // Selected issuers (updated reactively from form valueChanges)
  readonly csrIssuer = signal<GetIssuerCertificateResponse | null>(null);
  readonly autoIssuer = signal<GetIssuerCertificateResponse | null>(null);

  // CSR upload state
  readonly csrContent = signal<string | null>(null);
  readonly csrFilename = signal<string | null>(null);

  readonly csrForm = new FormGroup({
    issuerCertificateId: new FormControl<number | null>(null, [Validators.required]),
    notBefore: new FormControl(this.todayIso(), {
      nonNullable: true, validators: [Validators.required]
    }),
    notAfter: new FormControl(this.defaultNotAfterIso(), {
      nonNullable: true, validators: [Validators.required]
    })
  });

  readonly autoForm = new FormGroup({
    issuerCertificateId: new FormControl<number | null>(null, [Validators.required]),
    notBefore: new FormControl(this.todayIso(), {
      nonNullable: true, validators: [Validators.required]
    }),
    notAfter: new FormControl(this.defaultNotAfterIso(), {
      nonNullable: true, validators: [Validators.required]
    }),
    commonName: new FormControl('', {
      nonNullable: true, validators: [Validators.required, Validators.maxLength(255)]
    }),
    organization: new FormControl('', { nonNullable: true }),
    organizationalUnit: new FormControl('', { nonNullable: true }),
    country: new FormControl('', { nonNullable: true, validators: [countryValidator] }),
    emailAddress: new FormControl('', {
      nonNullable: true, validators: [Validators.email, Validators.maxLength(255)]
    }),
    keystoreFormat: new FormControl<'pkcs12' | 'jks'>('pkcs12', { nonNullable: true }),
    keystorePassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(8), Validators.maxLength(128)]
    })
  });

  // Per-tab feedback
  readonly csrBusy = signal(false);
  readonly csrError = signal<string | null>(null);
  readonly csrResult = signal<GetCertificateResponse | null>(null);
  readonly csrDownloadFormat = signal<'pem' | 'cer'>('pem');

  readonly autoBusy = signal(false);
  readonly autoError = signal<string | null>(null);
  readonly autoResult = signal<AutogenerateEeCertificateResponse | null>(null);
  readonly autoDownloadFormat = signal<'pem' | 'cer'>('pem');

  ngOnInit(): void {
    this.issueService.getAvailableIssuers().subscribe({
      next: (list) => this.issuers.set(list),
      error: () => this.issuers.set([])
    });

    this.csrForm.controls.issuerCertificateId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((id) => {
        this.csrIssuer.set(this.issuers().find((i) => i.id === id) ?? null);
      });

    this.autoForm.controls.issuerCertificateId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((id) => {
        this.autoIssuer.set(this.issuers().find((i) => i.id === id) ?? null);
      });
  }

  setTab(tab: 'csr' | 'autogenerate'): void {
    this.activeTab.set(tab);
  }

  onCsrFileChange(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.csrFilename.set(file.name);
    void file.text().then((content) => this.csrContent.set(content));
  }

  issuerDateMin(issuer: GetIssuerCertificateResponse | null): string {
    return issuer ? toDateOnly(issuer.notBefore) : '';
  }

  issuerDateMax(issuer: GetIssuerCertificateResponse | null): string {
    return issuer ? toDateOnly(issuer.notAfter) : '';
  }

  submitCsr(): void {
    this.csrError.set(null);
    if (this.csrForm.invalid) {
      this.csrForm.markAllAsTouched();
      this.csrError.set('Fix the highlighted fields.');
      return;
    }
    if (!this.csrContent()) {
      this.csrError.set('Please select a CSR .pem file.');
      return;
    }
    const v = this.csrForm.getRawValue();
    const req: CreateCsrCertificateRequest = {
      csrPem: this.csrContent()!,
      issuerCertificateId: v.issuerCertificateId!,
      notBefore: toIso(v.notBefore),
      notAfter: toIso(v.notAfter)
    };
    this.csrBusy.set(true);
    this.issueService.issueCsrCertificate(req).subscribe({
      next: (res) => { this.csrResult.set(res); this.csrBusy.set(false); },
      error: (err) => { this.csrError.set(extractError(err)); this.csrBusy.set(false); }
    });
  }

  submitAuto(): void {
    this.autoError.set(null);
    if (this.autoForm.invalid) {
      this.autoForm.markAllAsTouched();
      this.autoError.set('Fix the highlighted fields.');
      return;
    }
    const v = this.autoForm.getRawValue();
    const req: AutogenerateEeCertificateRequest = {
      issuerCertificateId: v.issuerCertificateId!,
      subject: {
        commonName: v.commonName.trim(),
        ...(v.organization.trim() && { organization: v.organization.trim() }),
        ...(v.organizationalUnit.trim() && { organizationalUnit: v.organizationalUnit.trim() }),
        ...(v.country.trim() && { country: v.country.trim().toUpperCase() }),
        ...(v.emailAddress.trim() && { emailAddress: v.emailAddress.trim() })
      },
      notBefore: toIso(v.notBefore),
      notAfter: toIso(v.notAfter),
      keystoreFormat: v.keystoreFormat,
      keystorePassword: v.keystorePassword
    };
    this.autoBusy.set(true);
    this.issueService.autogenerateEeCertificate(req).subscribe({
      next: (res) => {
        this.autoBusy.set(false);
        this.autoResult.set(res);
        this.triggerKeystoreDownload(res);
      },
      error: (err) => { this.autoError.set(extractError(err)); this.autoBusy.set(false); }
    });
  }

  triggerKeystoreDownload(res: AutogenerateEeCertificateResponse): void {
    const bytes = Uint8Array.from(atob(res.keystoreBase64), (c) => c.charCodeAt(0));
    const blob = new Blob([bytes], { type: 'application/octet-stream' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = res.keystoreFilename;
    a.click();
    URL.revokeObjectURL(url);
  }

  downloadPublicCert(id: number, format: 'pem' | 'cer'): void {
    this.certDownload.download(id, format);
  }

  resetCsr(): void {
    this.csrResult.set(null);
    this.csrError.set(null);
    this.csrContent.set(null);
    this.csrFilename.set(null);
    this.csrIssuer.set(null);
    this.csrForm.reset({ notBefore: this.todayIso(), notAfter: this.defaultNotAfterIso() });
  }

  resetAuto(): void {
    this.autoResult.set(null);
    this.autoError.set(null);
    this.autoIssuer.set(null);
    this.autoForm.reset({
      notBefore: this.todayIso(),
      notAfter: this.defaultNotAfterIso(),
      keystoreFormat: 'pkcs12'
    });
  }

  formatDate(value: string | null | undefined): string {
    if (!value) return '—';
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString();
  }

  // Error accessors
  get csrIssuerError(): string | null {
    const c = this.csrForm.controls.issuerCertificateId;
    return c.touched && c.invalid ? 'Issuer is required.' : null;
  }

  get autoIssuerError(): string | null {
    const c = this.autoForm.controls.issuerCertificateId;
    return c.touched && c.invalid ? 'Issuer is required.' : null;
  }

  get cnError(): string | null {
    const c = this.autoForm.controls.commonName;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'Common name (CN) is required.';
    if (c.hasError('maxlength')) return 'Max 255 characters.';
    return null;
  }

  get countryError(): string | null {
    const c = this.autoForm.controls.country;
    return c.touched && c.invalid ? 'Country code must be exactly 2 letters (e.g. RS).' : null;
  }

  get passwordError(): string | null {
    const c = this.autoForm.controls.keystorePassword;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'Password is required.';
    if (c.hasError('minlength')) return 'Password must be at least 8 characters.';
    if (c.hasError('maxlength')) return 'Password must be at most 128 characters.';
    return null;
  }

  private todayIso(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private defaultNotAfterIso(): string {
    const d = new Date();
    d.setFullYear(d.getFullYear() + 1);
    return d.toISOString().slice(0, 10);
  }
}
