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
    path: 'register',
    loadComponent: () =>
      import('./pages/register/register-page.component').then(
        (m) => m.RegisterPageComponent
      )
  },
  {
    path: 'activate',
    loadComponent: () =>
      import('./pages/activate/activate-page.component').then(
        (m) => m.ActivatePageComponent
      )
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./pages/forgot-password/forgot-password-page.component').then(
        (m) => m.ForgotPasswordPageComponent
      )
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./pages/reset-password/reset-password-page.component').then(
        (m) => m.ResetPasswordPageComponent
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
    path: 'regular/certificates/issue',
    loadComponent: () =>
      import('./pages/regular/regular-certificates-issue/regular-certificates-issue-page.component').then(
        (m) => m.RegularCertificatesIssuePageComponent
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
    path: 'admin/master-key',
    loadComponent: () =>
      import('./pages/admin/master-key/master-key-page.component').then(
        (m) => m.MasterKeyPageComponent
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
