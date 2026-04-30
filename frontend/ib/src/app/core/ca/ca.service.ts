import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GetCertificateResponse, SetPasswordRequest } from '../admin/admin.types';

@Injectable({ providedIn: 'root' })
export class CaService {
  constructor(private readonly http: HttpClient) {}

  getCertificates(): Observable<GetCertificateResponse[]> {
    return this.http.get<GetCertificateResponse[]>('/api/ca/certificates');
  }

  setPassword(req: SetPasswordRequest) {
    return this.http.put<void>('/api/ca/password', req);
  }
}
