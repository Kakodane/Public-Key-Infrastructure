import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RegularCertificateResponse } from './regular.types';

@Injectable({ providedIn: 'root' })
export class RegularService {
  constructor(private readonly http: HttpClient) {}

  getCertificates() {
    // #region agent log H3
    fetch('http://127.0.0.1:7301/ingest/71b3e8ec-8833-416a-b0a8-b91af0ec4f19', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': '9a9f7d' },
      body: JSON.stringify({
        sessionId: '9a9f7d',
        runId: 'pre-fix',
        hypothesisId: 'H3',
        location: 'regular.service.ts:10',
        message: 'RegularService.getCertificates invoked',
        data: { endpoint: '/api/regular/certificates' },
        timestamp: Date.now()
      })
    }).catch(() => {});
    // #endregion
    return this.http.get<RegularCertificateResponse[]>('/api/regular/certificates');
  }
}
