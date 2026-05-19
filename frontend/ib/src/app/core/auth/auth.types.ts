export type TokenResponse = {
  accessToken: string;
  expiresIn: number;
};

export type LoginResponse = {
  twoFactorRequired: boolean;
  twoFactorToken: string | null;
  accessToken: string | null;
  expiresIn: number | null;
};

export type MeResponse = {
  id: number;
  email: string;
  role: string;
  organizationId: number | null;
  organizationName: string | null;
  twoFactorEnabled: boolean;
};

export type TwoFactorSetupResponse = {
  secret: string;
  qrCodeUri: string;
};

