import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  CreateCaUserRequest,
  CreateCaUserResponse,
  CreateOrganizationRequest,
  CreateOrganizationResponse,
  GetOrgsResponse
} from './admin.types';

@Injectable({ providedIn: 'root' })
export class AdminService {
  constructor(private readonly http: HttpClient) {}

  createOrganization(req: CreateOrganizationRequest) {
    return this.http.post<CreateOrganizationResponse>('/api/admin/organization', req);
  }

  createCaUser(req: CreateCaUserRequest) {
    return this.http.post<CreateCaUserResponse>('/api/admin/ca-user', req);
  }

  getOrganizations() {
    return this.http.get<GetOrgsResponse[]>('/api/admin/organizations');
  }
}
