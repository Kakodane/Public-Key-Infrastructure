export type TokenResponse = {
  accessToken: string;
  expiresIn: number;
};

export type MeResponse = {
  id: number;
  email: string;
  role: string;
  organizationId: number | null;
  organizationName: string | null;
};

