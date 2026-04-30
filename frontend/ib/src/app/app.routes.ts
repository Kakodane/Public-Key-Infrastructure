import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { hasRoleGuard } from './core/auth/role.guard';
import { ROLE_ADMIN, ROLE_CA_USER, ROLE_REGULAR_USER } from './core/auth/role';

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
      import('./pages/regular/regular-certificates/regular-certificates-page.component').then(
        (m) => m.RegularCertificatesPageComponent
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
  {
    path: 'admin/certificates/issue',
    loadComponent: () =>
      import('./pages/admin/certificates-issue/certificates-issue-page.component').then(
        (m) => m.CertificatesIssuePageComponent
      ),
    canActivate: [authGuard, hasRoleGuard([ROLE_ADMIN])]
  },
  {
    path: 'ca/certificates',
    loadComponent: () =>
      import('./pages/ca/certificates/ca-certificates-page.component').then(
        (m) => m.CaCertificatesPageComponent
      ),
    canActivate: [authGuard, hasRoleGuard([ROLE_CA_USER])]
  },
  {
    path: 'ca/certificates/issue',
    loadComponent: () =>
      import('./pages/ca/certificates-issue/ca-certificates-issue-page.component').then(
        (m) => m.CaCertificatesIssuePageComponent
      ),
    canActivate: [authGuard, hasRoleGuard([ROLE_CA_USER])]
  },
  { path: '**', redirectTo: 'me' }
];
