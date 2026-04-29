import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { RegularService } from '../../core/regular/regular.service';
import { RegularCertificateResponse } from '../../core/regular/regular.types';

@Component({
  selector: 'app-my-certificates-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './my-certificates-page.component.html',
  styleUrl: './my-certificates-page.component.css'
})
export class MyCertificatesPageComponent implements OnInit {
  readonly certificates = signal<RegularCertificateResponse[]>([]);
  readonly isLoading = signal(false);
  readonly selectedCertificate = signal<RegularCertificateResponse | null>(null);

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
  }

  formatDate(value: string | Date | null | undefined): string {
    if (!value) return '—';
    const date = value instanceof Date ? value : new Date(value);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
  }

  labelType(value: string): string {
    return value.replaceAll('_', ' ');
  }
}
