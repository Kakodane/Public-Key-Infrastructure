export type ForgotPasswordRequest = {
  email: string;
};

export type PasswordResetChallengeResponse = {
  email: string;
  publicKeyPem: string;
  encryptedChallengeBase64: string;
};

export type ResetPasswordRequest = {
  token: string;
  decryptedChallengeBase64: string;
  newPassword: string;
  confirmPassword: string;
};