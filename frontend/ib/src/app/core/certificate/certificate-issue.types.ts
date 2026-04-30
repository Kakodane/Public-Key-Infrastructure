

export const CertificateKeyUsage = {
  DIGITAL_SIGNATURE: 'DIGITAL_SIGNATURE',
  NON_REPUDIATION: 'NON_REPUDIATION',
  KEY_ENCIPHERMENT: 'KEY_ENCIPHERMENT',
  DATA_ENCIPHERMENT: 'DATA_ENCIPHERMENT',
  KEY_AGREEMENT: 'KEY_AGREEMENT',
  KEY_CERT_SIGN: 'KEY_CERT_SIGN',
  CRL_SIGN: 'CRL_SIGN',
  ENCIPHER_ONLY: 'ENCIPHER_ONLY',
  DECIPHER_ONLY: 'DECIPHER_ONLY'
} as const;
export type CertificateKeyUsage = typeof CertificateKeyUsage[keyof typeof CertificateKeyUsage];

export const CertificateExtendedKeyUsage = {
  SERVER_AUTH: 'SERVER_AUTH',
  CLIENT_AUTH: 'CLIENT_AUTH',
  CODE_SIGNING: 'CODE_SIGNING',
  EMAIL_PROTECTION: 'EMAIL_PROTECTION',
  TIME_STAMPING: 'TIME_STAMPING',
  OCSP_SIGNING: 'OCSP_SIGNING'
} as const;
export type CertificateExtendedKeyUsage =
  typeof CertificateExtendedKeyUsage[keyof typeof CertificateExtendedKeyUsage];

export type X500NameRequest = {
  commonName: string;
  organization?: string;
  organizationalUnit?: string;
  country?: string;
  emailAddress?: string;
};

export type CertificateExtensionsRequest = {
  isCa?: boolean;
  pathLenConstraint?: number | null;
  keyUsages?: CertificateKeyUsage[];
  extendedKeyUsages?: CertificateExtendedKeyUsage[];
};

export type CreateRootCertificateRequest = {
  organizationId: number;
  subject: X500NameRequest;
  notBefore: string; // ISO-8601 instant
  notAfter: string;
  extensions?: CertificateExtensionsRequest;
};

export type CreateIntermediateCertificateRequest = {
  issuerCertificateId: number;
  organizationId: number;
  subject: X500NameRequest;
  notBefore: string;
  notAfter: string;
  extensions?: CertificateExtensionsRequest;
};

export type CreateEndEntityCertificateRequest = {
  issuerCertificateId: number;
  userEmail: string;
  subject: X500NameRequest;
  notBefore: string;
  notAfter: string;
  extensions?: CertificateExtensionsRequest;
};
