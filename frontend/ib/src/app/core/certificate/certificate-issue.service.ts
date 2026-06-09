import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GetCertificateResponse } from '../admin/admin.types';
import {
  AutogenerateEeCertificateRequest,
  AutogenerateEeCertificateResponse,
  CreateCsrCertificateRequest,
  CreateEndEntityCertificateRequest,
  CreateIntermediateCertificateRequest,
  CreateRootCertificateRequest,
  GetIssuerCertificateResponse
} from './certificate-issue.types';


@Injectable({ providedIn: 'root' })
export class CertificateIssueService {
  constructor(private readonly http: HttpClient) {}

  issueRoot(request: CreateRootCertificateRequest): Observable<GetCertificateResponse> {
    return this.http.post<GetCertificateResponse>('/api/certificates/root', request);
  }

  issueIntermediate(
    request: CreateIntermediateCertificateRequest
  ): Observable<GetCertificateResponse> {
    return this.http.post<GetCertificateResponse>('/api/certificates/intermediate', request);
  }

  issueEndEntity(
    request: CreateEndEntityCertificateRequest
  ): Observable<GetCertificateResponse> {
    return this.http.post<GetCertificateResponse>('/api/certificates/end-entity', request);
  }

  getAvailableIssuers(): Observable<GetIssuerCertificateResponse[]> {
    return this.http.get<GetIssuerCertificateResponse[]>('/api/certificates/issuers');
  }

  issueCsrCertificate(request: CreateCsrCertificateRequest): Observable<GetCertificateResponse> {
    return this.http.post<GetCertificateResponse>('/api/certificates/csr', request);
  }

  autogenerateEeCertificate(
    request: AutogenerateEeCertificateRequest
  ): Observable<AutogenerateEeCertificateResponse> {
    return this.http.post<AutogenerateEeCertificateResponse>('/api/certificates/autogenerate', request);
  }
}
