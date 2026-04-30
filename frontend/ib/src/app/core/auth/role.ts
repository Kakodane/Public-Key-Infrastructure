export const ROLE_ADMIN = 'ROLE_ADMIN';
export const ROLE_CA_USER = 'ROLE_CA_USER';
export const ROLE_REGULAR_USER = 'ROLE_REGULAR_USER';

export type AppRole =
  | typeof ROLE_ADMIN
  | typeof ROLE_CA_USER
  | typeof ROLE_REGULAR_USER;

export function isAdmin(role: string | null | undefined): boolean {
  return role === ROLE_ADMIN;
}

export function isCaUser(role: string | null | undefined): boolean {
  return role === ROLE_CA_USER;
}

export function isRegularUser(role: string | null | undefined): boolean {
  return role === ROLE_REGULAR_USER;
}
