import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';

export type CertificateDownloadFormat = 'pem' | 'cer' | 'pkcs12';

@Injectable({ providedIn: 'root' })
export class CertificateDownloadService {
  constructor(private readonly http: HttpClient) {}

  download(
    id: number,
    format: CertificateDownloadFormat,
    keystorePassword?: string,
    onError?: (message: string) => void
  ): void {
    const request =
      format === 'pkcs12'
        ? this.http.post(`/api/certificates/${id}/download`, { format, keystorePassword }, {
            responseType: 'blob',
            observe: 'response'
          })
        : this.http.get(`/api/certificates/${id}/download`, {
            params: { format },
            responseType: 'blob',
            observe: 'response'
          });

    const fallbackExt = format === 'pkcs12' ? 'p12' : format;

    request.subscribe({
      next: (response) => this.saveBlob(response, fallbackExt),
      error: (err) => {
        void this.resolveErrorMessage(err).then((message) => {
          if (onError) onError(message);
        });
      }
    });
  }

  private saveBlob(response: HttpResponse<Blob>, fallbackExt: string): void {
    const blob = response.body;
    if (!blob) return;

    const contentDisposition = response.headers.get('Content-Disposition');
    const filename = this.extractFilename(contentDisposition) ?? `certificate.${fallbackExt}`;

    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private extractFilename(contentDisposition: string | null): string | null {
    if (!contentDisposition) return null;
    const match = contentDisposition.match(/filename="?([^";\s]+)"?/);
    return match?.[1] ?? null;
  }

  private async resolveErrorMessage(err: unknown): Promise<string> {
    if (!(err instanceof HttpErrorResponse)) {
      return 'Download failed';
    }

    const body = err.error;
    if (body instanceof Blob) {
      try {
        const text = await body.text();
        const parsed = JSON.parse(text) as { message?: string; errors?: string[] };
        if (parsed.errors?.length) return parsed.errors.join(', ');
        if (parsed.message) return parsed.message;
      } catch {
        return err.message || 'Download failed';
      }
    }

    if (body && typeof body === 'object') {
      const parsed = body as { message?: string; errors?: string[] };
      if (parsed.errors?.length) return parsed.errors.join(', ');
      if (parsed.message) return parsed.message;
    }

    return err.message || 'Download failed';
  }
}
