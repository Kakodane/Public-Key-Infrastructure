import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RevocationReason } from './certificate.types';

@Injectable({ providedIn: 'root' })
export class CertificateRevokeService {
  constructor(private readonly http: HttpClient) {}

  revoke(id: number, reason: RevocationReason): Observable<void> {
    return this.http.post<void>(`/api/certificates/${id}/revoke`, { reason });
  }
}