import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { CertificateDownloadService } from '../../../core/certificate/certificate-download.service';
import { RegularService } from '../../../core/regular/regular.service';
import { RegularCertificateResponse } from '../../../core/regular/regular.types';
import { CertificateRevokeService } from '../../../core/certificate/certificate-revoke.service';
import { RevocationReason, REVOCATION_REASONS, labelRevocationReason } from '../../../core/certificate/certificate.types';

@Component({
  selector: 'app-regular-certificates-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './regular-certificates-page.component.html',
  styleUrl: './regular-certificates-page.component.css'
})
export class RegularCertificatesPageComponent implements OnInit {
  private readonly certDownload = inject(CertificateDownloadService);
  private readonly certRevoke = inject(CertificateRevokeService);
  readonly certificates = signal<RegularCertificateResponse[]>([]);
  readonly isLoading = signal(false);
  readonly selectedCertificate = signal<RegularCertificateResponse | null>(null);
  readonly downloadFormat = signal<'pem' | 'cer'>('pem');
  readonly revocationReasons = REVOCATION_REASONS;
  readonly revokeReason = signal<RevocationReason>(RevocationReason.UNSPECIFIED);
  readonly revoking = signal(false);
  readonly revokeError = signal<string | null>(null);

  readonly labelRevocationReason = labelRevocationReason;
  constructor(private readonly regularService: RegularService) {}

  ngOnInit(): void {
    this.loadCertificates();
  }

  loadCertificates(): void {
    this.isLoading.set(true);
    this.regularService.getCertificates().subscribe({
      next: (certs) => {
        this.certificates.set(certs);
        const selected = this.selectedCertificate();
        if (selected) {
          this.selectedCertificate.set(
            certs.find((c) => c.id === selected.id) ?? null
          );
        }
      },
      error: () => this.isLoading.set(false),
      complete: () => this.isLoading.set(false)
    });
  }

  openDetails(certificate: RegularCertificateResponse): void {
    this.selectedCertificate.set(certificate);
  }

  clearDetails(): void {
    this.selectedCertificate.set(null);
    this.revokeError.set(null);
    this.revokeReason.set(RevocationReason.UNSPECIFIED);
  }

  revoke(id: number): void {
    this.revokeError.set(null);
    const reason = this.revokeReason();
    if (!confirm('Revoke this certificate? This action cannot be undone.')) {
      return;
    }
    this.revoking.set(true);
    this.certRevoke.revoke(id, reason).subscribe({
      next: () => this.loadCertificates(),
      error: (err) => {
        this.revokeError.set(err?.error?.message ?? err?.message ?? 'Revocation failed');
        this.revoking.set(false);
      },
      complete: () => this.revoking.set(false)
    });
  }
  formatDate(value: string | Date | null | undefined): string {
    if (!value) return '—';
    const date = value instanceof Date ? value : new Date(value);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
  }

  labelType(value: string): string {
    return value.replaceAll('_', ' ');
  }

  downloadCertificate(id: number, format: 'pem' | 'cer') {
    this.certDownload.download(id, format);
  }
}
