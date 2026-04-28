import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () =>
      import('./pages/login/login-page.component').then((m) => m.LoginPageComponent)
  },
  {
    path: 'me',
    loadComponent: () =>
      import('./pages/me/me-page.component').then((m) => m.MePageComponent),
    canActivate: [authGuard]
  },
  { path: '**', redirectTo: 'me' }
];
