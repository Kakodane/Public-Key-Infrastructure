import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class CertificateDownloadService {
  constructor(private readonly http: HttpClient) {}

  download(id: number, format: 'pem' | 'cer'): void {
    this.http
      .get(`/api/certificates/${id}/download`, {
        params: { format },
        responseType: 'blob',
        observe: 'response'
      })
      .subscribe((response) => {
        const blob = response.body;
        if (!blob) return;

        const contentDisposition = response.headers.get('Content-Disposition');
        const filename = this.extractFilename(contentDisposition) ?? `certificate.${format}`;

        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        anchor.click();
        URL.revokeObjectURL(url);
      });
  }

  private extractFilename(contentDisposition: string | null): string | null {
    if (!contentDisposition) return null;
    const match = contentDisposition.match(/filename="?([^";\s]+)"?/);
    return match?.[1] ?? null;
  }
}
