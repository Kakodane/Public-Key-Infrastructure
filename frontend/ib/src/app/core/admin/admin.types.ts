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
