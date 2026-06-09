
export enum CertificateType{
  ROOT = 'ROOT',
  INTERMEDIATE = 'INTERMEDIATE',
  END_ENTITY = 'END_ENTITY'
}
export enum CertificateStatus{
  ACTIVE = 'ACTIVE',
  REVOKED = 'REVOKED'
}
export enum RevocationReason{
  UNSPECIFIED = 'UNSPECIFIED',
	KEY_COMPROMISE = 'KEY_COMPROMISE',
	CA_COMPROMISE = 'CA_COMPROMISE',
	AFFILIATION_CHANGED = 'AFFILIATION_CHANGED',
	SUPERSEDED = 'SUPERSEDED',
	CESSATION_OF_OPERATION = 'CESSATION_OF_OPERATION',
	CERTIFICATE_HOLD = 'CERTIFICATE_HOLD',
	REMOVE_FROM_CRL = 'REMOVE_FROM_CRL',
	PRIVILEGE_WITHDRAWN = 'PRIVILEGE_WITHDRAWN',
	AA_COMPROMISE = 'AA_COMPROMISE'
}

export const REVOCATION_REASONS: RevocationReason[] = [
  RevocationReason.UNSPECIFIED,
  RevocationReason.KEY_COMPROMISE,
  RevocationReason.CA_COMPROMISE,
  RevocationReason.AFFILIATION_CHANGED,
  RevocationReason.SUPERSEDED,
  RevocationReason.CESSATION_OF_OPERATION,
  RevocationReason.CERTIFICATE_HOLD,
  RevocationReason.REMOVE_FROM_CRL,
  RevocationReason.PRIVILEGE_WITHDRAWN,
  RevocationReason.AA_COMPROMISE
];
export function labelRevocationReason(value: RevocationReason | string | null | undefined): string {
  if (!value) return '—';
  return value.replaceAll('_', ' ');
}