import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SetPasswordRequest } from '../admin/admin.types';

@Injectable({ providedIn: 'root' })
export class CaService {
  constructor(private readonly http: HttpClient) {}

  setPassword(req: SetPasswordRequest) {
    return this.http.put<void>('/api/ca/password', req);
  }
}
