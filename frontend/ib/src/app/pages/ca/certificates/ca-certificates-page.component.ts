import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CaService } from '../../../core/ca/ca.service';
import { CertificateDownloadService } from '../../../core/certificate/certificate-download.service';
import { GetCertificateResponse } from '../../../core/admin/admin.types';
import { CertificateStatus, RevocationReason } from '../../../core/certificate/certificate.types';

@Component({
  selector: 'app-ca-certificates-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './ca-certificates-page.component.html',
  styleUrl: './ca-certificates-page.component.css'
})
export class CaCertificatesPageComponent implements OnInit {
  private readonly ca = inject(CaService);
  private readonly certDownload = inject(CertificateDownloadService);

  readonly certificates = signal<GetCertificateResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly selectedCertificate = signal<GetCertificateResponse | null>(null);
  readonly downloadFormat = signal<'pem' | 'cer'>('pem');

  readonly hasCertificates = computed(() => this.certificates().length > 0);

  ngOnInit() {
    this.loadCertificates();
  }

  loadCertificates() {
    this.error.set(null);
    this.loading.set(true);
    this.ca.getCertificates().subscribe({
      next: (certificates) => {
        this.certificates.set(certificates);
        const selected = this.selectedCertificate();
        if (selected) {
          this.selectedCertificate.set(
            certificates.find((c) => c.id === selected.id) ?? null
          );
        }
      },
      error: (err) => {
        this.certificates.set([]);
        this.selectedCertificate.set(null);
        this.error.set(extractError(err));
      },
      complete: () => this.loading.set(false)
    });
  }

  openDetails(certificate: GetCertificateResponse) {
    this.selectedCertificate.set(certificate);
  }

  clearDetails() {
    this.selectedCertificate.set(null);
  }

  formatDate(value: string | null | undefined) {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
  }

  labelType(value: string) {
    return value.replaceAll('_', ' ');
  }

  labelRevocationReason(value: RevocationReason | null) {
    if (value == null) return '—';
    return RevocationReason[value].replaceAll('_', ' ');
  }

  downloadCertificate(id: number, format: 'pem' | 'cer') {
    this.certDownload.download(id, format);
  }
}

function extractError(err: any): string {
  if (err?.error?.errors?.length) return err.error.errors.join(', ');
  return err?.error?.message ?? err?.message ?? 'Request failed';
}
