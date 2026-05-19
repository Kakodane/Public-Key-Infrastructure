import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ForgotPasswordRequest,
  PasswordResetChallengeResponse,
  ResetPasswordRequest
} from './password-reset.types';

@Injectable({ providedIn: 'root' })
export class PasswordResetService {
  constructor(private readonly http: HttpClient) {}

  requestReset(req: ForgotPasswordRequest): Observable<void> {
    return this.http.post<void>('/api/password-reset/request', req);
  }

  getResetChallenge(token: string): Observable<PasswordResetChallengeResponse> {
    return this.http.get<PasswordResetChallengeResponse>(
      `/api/password-reset/${encodeURIComponent(token)}/challenge`
    );
  }

  completeReset(req: ResetPasswordRequest): Observable<void> {
    return this.http.post<void>('/api/password-reset/complete', req);
  }
}