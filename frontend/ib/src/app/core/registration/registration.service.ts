import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ActivateAccountRequest,
  ActivationChallengeResponse,
  RegisterRequest
} from './registration.types';

@Injectable({ providedIn: 'root' })
export class RegistrationService {
  constructor(private readonly http: HttpClient) {}

  register(req: RegisterRequest): Observable<void> {
    return this.http.post<void>('/api/registration', req);
  }

  getActivationChallenge(token: string): Observable<ActivationChallengeResponse> {
    return this.http.get<ActivationChallengeResponse>(
      `/api/registration/${encodeURIComponent(token)}/challenge`
    );
  }

  activate(req: ActivateAccountRequest): Observable<void> {
    return this.http.post<void>('/api/registration/activate', req);
  }
}