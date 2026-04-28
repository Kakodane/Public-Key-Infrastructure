import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { TokenStorageService } from './token-storage.service';

export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  const tokenStorage = inject(TokenStorageService);
  const tokens = tokenStorage.read();
  if (tokens?.accessToken) return true;
  return router.parseUrl('/login');
};

