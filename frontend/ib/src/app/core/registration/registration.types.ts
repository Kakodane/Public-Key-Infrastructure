export type RegisterRequest = {
  email: string;
  password: string;
  confirmPassword: string;
  firstName: string;
  lastName: string;
  organizationName: string;
  publicKeyPem: string;
};

export type ActivationChallengeResponse = {
  email: string;
  publicKeyPem: string;
  encryptedChallengeBase64: string;
};

export type ActivateAccountRequest = {
  token: string;
  decryptedChallengeBase64: string;
};