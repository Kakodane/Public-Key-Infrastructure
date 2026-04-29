export type RegularCertificateResponse = {
  id: number;
  serialNumber: string;
  type: string;
  status: string;
  subjectCommonName: string;
  organizationName: string;
  issuerSerialNumber: string | null;
  notBefore: string;
  notAfter: string;
  revokedAt: string | null;
  revocationReason: string | null;
};
