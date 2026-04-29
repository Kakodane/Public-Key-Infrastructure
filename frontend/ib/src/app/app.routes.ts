import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { hasRoleGuard } from './core/auth/role.guard';
import { ROLE_ADMIN, ROLE_REGULAR_USER } from './core/auth/role';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () =>
      import('./pages/login/login-page.component').then((m) => m.LoginPageComponent)
  },
  {
    path: 'set-password',
    loadComponent: () =>
      import('./pages/set-password/set-password-page.component').then(
        (m) => m.SetPasswordPageComponent
      )
  },
  {
    path: 'me',
    loadComponent: () =>
      import('./pages/me/me-page.component').then((m) => m.MePageComponent),
    canActivate: [authGuard]
  },
  {
    path: 'admin/ca-users',
    loadComponent: () =>
      import('./pages/admin/ca-users/ca-users-page.component').then(
        (m) => m.CaUsersPageComponent
      ),
    canActivate: [authGuard, hasRoleGuard([ROLE_ADMIN])]
  },
  {
    path: 'regular/certificates',
    loadComponent: () =>
      import('./pages/regular/my-certificates-page.component').then(
        (m) => m.MyCertificatesPageComponent
      ),
    canActivate: [authGuard, hasRoleGuard([ROLE_REGULAR_USER])]
  },
  {
    path: 'admin/certificates',
    loadComponent: () =>
      import('./pages/admin/certificates/certificates-page.component').then(
        (m) => m.CertificatesPageComponent
      ),
    canActivate: [authGuard, hasRoleGuard([ROLE_ADMIN])]
  },
  { path: '**', redirectTo: 'me' }
];
