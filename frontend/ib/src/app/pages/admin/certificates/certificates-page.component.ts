import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AdminService } from '../../../core/admin/admin.service';
import {CertificateStatus, CertificateType, RevocationReason} from '../../../core/certificate/certificate.types';
import { GetCertificateResponse } from '../../../core/admin/admin.types';

@Component({
  selector: 'app-certificates-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './certificates-page.component.html',
  styleUrl: './certificates-page.component.css'
})
export class CertificatesPageComponent implements OnInit {
  private readonly admin = inject(AdminService);

  readonly certificates = signal<GetCertificateResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly selectedCertificate = signal<GetCertificateResponse | null>(null);

  readonly hasCertificates = computed(() => this.certificates().length > 0);

  ngOnInit() {
    this.loadCertificates();
  }

  loadCertificates() {
    this.error.set(null);
    this.loading.set(true);
    this.admin.getCertificates().subscribe({
      next: (certificates) => {
        this.certificates.set(certificates);
        const selected = this.selectedCertificate();
        if (selected) {
          this.selectedCertificate.set(
            certificates.find((certificate) => certificate.id === selected.id) ?? null
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

  formatDate(value: string | Date | null | undefined) {
    if (!value) return '—';
    const date = value instanceof Date ? value : new Date(value);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
  }

  labelType(value: CertificateType) {
    return value.replaceAll('_', ' ');
  }

  labelStatus(value: CertificateStatus) {
    return value;
  }

  labelRevocationReason(value: RevocationReason | null) {
    if (value == null) return '—';
    return RevocationReason[value].replaceAll('_', ' ');
  }
}

function extractError(err: any): string {
  if (err?.error?.errors?.length) {
    return err.error.errors.join(', ');
  }
  return err?.error?.message ?? err?.message ?? 'Request failed';
}