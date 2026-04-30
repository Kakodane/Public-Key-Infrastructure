import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { CaService } from '../../../core/ca/ca.service';
import { GetCertificateResponse } from '../../../core/admin/admin.types';
import { CertificateIssueService } from '../../../core/certificate/certificate-issue.service';
import {
  CertificateStatus,
  CertificateType
} from '../../../core/certificate/certificate.types';
import {
  CertificateExtendedKeyUsage,
  CertificateExtensionsRequest,
  CertificateKeyUsage,
  CreateEndEntityCertificateRequest,
  CreateIntermediateCertificateRequest,
  X500NameRequest
} from '../../../core/certificate/certificate-issue.types';

const KEY_USAGE_OPTIONS: { id: CertificateKeyUsage; label: string }[] = [
  { id: CertificateKeyUsage.DIGITAL_SIGNATURE, label: 'digitalSignature' },
  { id: CertificateKeyUsage.NON_REPUDIATION, label: 'nonRepudiation' },
  { id: CertificateKeyUsage.KEY_ENCIPHERMENT, label: 'keyEncipherment' },
  { id: CertificateKeyUsage.DATA_ENCIPHERMENT, label: 'dataEncipherment' },
  { id: CertificateKeyUsage.KEY_AGREEMENT, label: 'keyAgreement' },
  { id: CertificateKeyUsage.KEY_CERT_SIGN, label: 'keyCertSign (CA only)' },
  { id: CertificateKeyUsage.CRL_SIGN, label: 'cRLSign (CA only)' },
  { id: CertificateKeyUsage.ENCIPHER_ONLY, label: 'encipherOnly' },
  { id: CertificateKeyUsage.DECIPHER_ONLY, label: 'decipherOnly' }
];

const EXT_KEY_USAGE_OPTIONS: { id: CertificateExtendedKeyUsage; label: string }[] = [
  { id: CertificateExtendedKeyUsage.SERVER_AUTH, label: 'serverAuth (TLS server)' },
  { id: CertificateExtendedKeyUsage.CLIENT_AUTH, label: 'clientAuth (TLS client)' },
  { id: CertificateExtendedKeyUsage.CODE_SIGNING, label: 'codeSigning' },
  { id: CertificateExtendedKeyUsage.EMAIL_PROTECTION, label: 'emailProtection (S/MIME)' },
  { id: CertificateExtendedKeyUsage.TIME_STAMPING, label: 'timeStamping' },
  { id: CertificateExtendedKeyUsage.OCSP_SIGNING, label: 'OCSPSigning' }
];

function countryLengthValidator(control: AbstractControl): ValidationErrors | null {
  const v = (control.value ?? '').trim();
  if (v === '') return null;
  return v.length === 2 ? null : { length: 'Country code must be exactly 2 characters' };
}

@Component({
  selector: 'app-ca-certificates-issue-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './ca-certificates-issue-page.component.html',
  styleUrl: './ca-certificates-issue-page.component.css'
})
export class CaCertificatesIssuePageComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly ca = inject(CaService);
  private readonly issuer = inject(CertificateIssueService);
  private readonly destroyRef = inject(DestroyRef);

  readonly CertificateType = CertificateType;
  readonly keyUsageOptions = KEY_USAGE_OPTIONS;
  readonly extKeyUsageOptions = EXT_KEY_USAGE_OPTIONS;

  readonly availableIssuers = signal<GetCertificateResponse[]>([]);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  readonly organizationId = computed(() => this.auth.me()?.organizationId ?? null);
  readonly organizationName = computed(() => this.auth.me()?.organizationName ?? '—');

  get issuerError(): string | null {
    const c = this.form.controls.issuerCertificateId;
    if (!c.touched || c.valid) return null;
    return 'Issuer certificate is required.';
  }

  get userEmailError(): string | null {
    const c = this.form.controls.userEmail;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'Owner email is required.';
    if (c.hasError('email')) return 'Enter a valid email address.';
    return null;
  }

  get commonNameError(): string | null {
    const c = this.form.controls.subject.controls.commonName;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'Common name (CN) is required.';
    if (c.hasError('maxlength')) return 'Common name must be at most 255 characters.';
    return null;
  }

  get countryError(): string | null {
    const c = this.form.controls.subject.controls.country;
    if (!c.touched || c.valid) return null;
    return 'Country code must be exactly 2 letters (e.g. RS).';
  }

  get subjectEmailError(): string | null {
    const c = this.form.controls.subject.controls.emailAddress;
    if (!c.touched || c.valid) return null;
    if (c.hasError('email')) return 'Enter a valid email address.';
    if (c.hasError('maxlength')) return 'Email must be at most 255 characters.';
    return null;
  }

  readonly form = new FormGroup({
    type: new FormControl<CertificateType>(CertificateType.INTERMEDIATE, {
      nonNullable: true,
      validators: [Validators.required]
    }),
    issuerCertificateId: new FormControl<number | null>(null),
    userEmail: new FormControl<string | null>(null),

    subject: new FormGroup({
      commonName: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(255)]
      }),
      organization: new FormControl('', { nonNullable: true }),
      organizationalUnit: new FormControl('', { nonNullable: true }),
      country: new FormControl('', {
        nonNullable: true,
        validators: [countryLengthValidator]
      }),
      emailAddress: new FormControl('', {
        nonNullable: true,
        validators: [Validators.email, Validators.maxLength(255)]
      })
    }),

    notBefore: new FormControl(this.todayIso(), {
      nonNullable: true,
      validators: [Validators.required]
    }),
    notAfter: new FormControl(this.defaultNotAfterIso(), {
      nonNullable: true,
      validators: [Validators.required]
    }),

    extensions: new FormGroup({
      isCa: new FormControl(false, { nonNullable: true }),
      pathLenConstraint: new FormControl<number | null>(null, {
        validators: [Validators.min(0)]
      }),
      keyUsages: new FormGroup(
        Object.fromEntries(
          KEY_USAGE_OPTIONS.map((b) => [b.id, new FormControl(false, { nonNullable: true })])
        ) as Record<CertificateKeyUsage, FormControl<boolean>>
      ),
      extendedKeyUsages: new FormGroup(
        Object.fromEntries(
          EXT_KEY_USAGE_OPTIONS.map((b) => [b.id, new FormControl(false, { nonNullable: true })])
        ) as Record<CertificateExtendedKeyUsage, FormControl<boolean>>
      )
    })
  });

  private readonly typeSignal = signal<CertificateType>(CertificateType.INTERMEDIATE);

  readonly isEndEntity = computed(() => this.typeSignal() === CertificateType.END_ENTITY);

  ngOnInit() {
    this.form.controls.type.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((t) => {
        this.typeSignal.set(t);
        this.applyTypeDefaults(t);
      });
    this.applyTypeDefaults(this.form.controls.type.value);
    this.loadIssuers();
  }

  private loadIssuers() {
    this.ca.getCertificates().subscribe({
      next: (certs) => {
        this.availableIssuers.set(
          certs.filter(
            (c) =>
              c.status === CertificateStatus.ACTIVE &&
              c.type !== CertificateType.END_ENTITY
          )
        );
      },
      error: () => this.availableIssuers.set([])
    });
  }

  private applyTypeDefaults(t: CertificateType) {
    const ext = this.form.controls.extensions;
    const ku = ext.controls.keyUsages.controls;
    const eku = ext.controls.extendedKeyUsages.controls;

    for (const usage of KEY_USAGE_OPTIONS) ku[usage.id].setValue(false);
    for (const usage of EXT_KEY_USAGE_OPTIONS) eku[usage.id].setValue(false);

    this.form.controls.issuerCertificateId.clearValidators();
    this.form.controls.userEmail.clearValidators();

    this.form.controls.issuerCertificateId.setValidators([Validators.required]);

    switch (t) {
      case CertificateType.INTERMEDIATE:
        ext.controls.isCa.setValue(true);
        ku[CertificateKeyUsage.KEY_CERT_SIGN].setValue(true);
        ku[CertificateKeyUsage.CRL_SIGN].setValue(true);
        break;
      case CertificateType.END_ENTITY:
        this.form.controls.userEmail.setValidators([Validators.required, Validators.email]);
        ext.controls.isCa.setValue(false);
        ext.controls.pathLenConstraint.setValue(null);
        ku[CertificateKeyUsage.DIGITAL_SIGNATURE].setValue(true);
        ku[CertificateKeyUsage.KEY_ENCIPHERMENT].setValue(true);
        break;
    }
    this.form.controls.issuerCertificateId.updateValueAndValidity();
    this.form.controls.userEmail.updateValueAndValidity();
  }

  submit() {
    this.error.set(null);
    this.success.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Form has validation errors — fix the highlighted fields.');
      return;
    }

    const orgId = this.organizationId();
    if (orgId == null) {
      this.error.set('Organization context not available. Please re-login.');
      return;
    }

    const v = this.form.getRawValue();
    const type = v.type;

    const ku = collectChecked(v.extensions.keyUsages) as CertificateKeyUsage[];
    if (!v.extensions.isCa && ku.includes(CertificateKeyUsage.KEY_CERT_SIGN)) {
      this.error.set('keyCertSign requires CA = true. Toggle CA on or remove keyCertSign.');
      return;
    }

    const subject = buildSubject(v.subject);
    const extensions = buildExtensions(v.extensions);
    const notBefore = toIso(v.notBefore);
    const notAfter = toIso(v.notAfter);

    this.busy.set(true);

    if (type === CertificateType.INTERMEDIATE) {
      const req: CreateIntermediateCertificateRequest = {
        issuerCertificateId: v.issuerCertificateId!,
        organizationId: orgId,
        subject,
        notBefore,
        notAfter,
        extensions
      };
      this.issuer.issueIntermediate(req).subscribe(this.handleResponse());
    } else {
      const req: CreateEndEntityCertificateRequest = {
        issuerCertificateId: v.issuerCertificateId!,
        userEmail: v.userEmail!,
        subject,
        notBefore,
        notAfter,
        extensions
      };
      this.issuer.issueEndEntity(req).subscribe(this.handleResponse());
    }
  }

  private handleResponse() {
    return {
      next: (res: GetCertificateResponse) => {
        this.success.set(`Certificate issued — serial ${res.serialNumber}`);
        this.form.reset({
          type: CertificateType.INTERMEDIATE,
          notBefore: this.todayIso(),
          notAfter: this.defaultNotAfterIso()
        });
        this.applyTypeDefaults(CertificateType.INTERMEDIATE);
        this.loadIssuers();
      },
      error: (err: any) => {
        this.error.set(extractError(err));
        this.busy.set(false);
      },
      complete: () => this.busy.set(false)
    };
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

function buildSubject(s: {
  commonName: string;
  organization: string;
  organizationalUnit: string;
  country: string;
  emailAddress: string;
}): X500NameRequest {
  const out: X500NameRequest = { commonName: s.commonName.trim() };
  if (s.organization?.trim()) out.organization = s.organization.trim();
  if (s.organizationalUnit?.trim()) out.organizationalUnit = s.organizationalUnit.trim();
  if (s.country?.trim()) out.country = s.country.trim().toUpperCase();
  if (s.emailAddress?.trim()) out.emailAddress = s.emailAddress.trim();
  return out;
}

function buildExtensions(e: {
  isCa: boolean;
  pathLenConstraint: number | null;
  keyUsages: Record<CertificateKeyUsage, boolean>;
  extendedKeyUsages: Record<CertificateExtendedKeyUsage, boolean>;
}): CertificateExtensionsRequest {
  const keyUsages = collectChecked(e.keyUsages) as CertificateKeyUsage[];
  const extendedKeyUsages = collectChecked(e.extendedKeyUsages) as CertificateExtendedKeyUsage[];
  return {
    isCa: e.isCa,
    pathLenConstraint: e.isCa ? e.pathLenConstraint : null,
    keyUsages: keyUsages.length ? keyUsages : undefined,
    extendedKeyUsages: extendedKeyUsages.length ? extendedKeyUsages : undefined
  };
}

function collectChecked<T extends string>(map: Record<T, boolean>): T[] {
  return (Object.entries(map) as [T, boolean][])
    .filter(([, on]) => on)
    .map(([id]) => id);
}

function toIso(dateOnly: string): string {
  if (dateOnly.length === 10) return new Date(dateOnly + 'T00:00:00Z').toISOString();
  return new Date(dateOnly).toISOString();
}

function extractError(err: any): string {
  if (err?.error?.errors?.length) return err.error.errors.join(', ');
  return err?.error?.message ?? err?.message ?? 'Issuance failed';
}
