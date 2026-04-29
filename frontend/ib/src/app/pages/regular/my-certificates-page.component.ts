import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { RegularService } from '../../core/regular/regular.service';
import { RegularCertificateResponse } from '../../core/regular/regular.types';

@Component({
  selector: 'app-my-certificates-page',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './my-certificates-page.component.html',
  styleUrl: './my-certificates-page.component.css'
})
export class MyCertificatesPageComponent implements OnInit {
  certificates = signal<RegularCertificateResponse[]>([]);
  isLoading = signal(false);

  constructor(private readonly regularService: RegularService) {}

  ngOnInit(): void {
    this.isLoading.set(true);
    this.regularService.getCertificates().subscribe({
      next: (certs) => {
        this.certificates.set(certs);
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
      }
    });
  }
}
