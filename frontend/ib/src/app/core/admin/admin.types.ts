import { CertificateStatus, CertificateType, RevocationReason } from "../certificate/certificate.types";

export type CreateOrganizationRequest = {
  name: string;
};

export type CreateOrganizationResponse = {
  id: number;
  name: string;
};

export type CreateCaUserRequest = {
  email: string;
  firstName: string;
  lastName: string;
  orgId: number;
};

export type CreateCaUserResponse = {
  id: number;
};

export type GetOrgsResponse = {
  id: number;
  name: string;
};

export type SetPasswordRequest = {
  token: string;
  password: string;
};

export type GetCertificateResponse={
  id: number;
  serialNumber: string;
  type: CertificateType;
  status: CertificateStatus;
  subjectCommonName: string;
  organizationName: string;
  issuerSerialNumber: string | null;
  notBefore: string;
  notAfter: string;
  revokedAt: string | null;
  revocationReason:RevocationReason | null;
}

export type MasterKeyRotationJobStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';

export type MasterKeyStatusResponse = {
  currentVersion: number;
  createdAt: string;
  lastRotationAt: string | null;
  jobStatus: MasterKeyRotationJobStatus | null;
};

export type MasterKeyRotationResponse = {
  jobId: number;
  status: MasterKeyRotationJobStatus;
};
