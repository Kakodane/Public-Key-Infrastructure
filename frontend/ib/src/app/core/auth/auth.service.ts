import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, map, of, switchMap, tap } from 'rxjs';
import { TokenStorageService } from './token-storage.service';
import { LoginResponse, MeResponse, TwoFactorSetupResponse } from './auth.types';

export type LoginResult =
  | { twoFactorRequired: true; twoFactorToken: string }
  | { twoFactorRequired: false };

type AuthState =
  | { status: 'anonymous' }
  | { status: 'authenticated'; me: MeResponse }
  | { status: 'error'; message: string };

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly state = signal<AuthState>({ status: 'anonymous' });
  readonly status = computed(() => this.state().status);
  readonly me = computed(() => {
    const s = this.state();
    return s.status === 'authenticated' ? s.me : null;
  });

  constructor(
    private readonly http: HttpClient,
    private readonly tokenStorage: TokenStorageService
  ) {
    if (this.tokenStorage.read()) {
      this.loadMe().subscribe();
    }
  }

  login(email: string, password: string) {
    return this.http.post<LoginResponse>('/api/auth/login', { email, password }).pipe(
      switchMap((r) => {
        if (r.twoFactorRequired) {
          return of({
            twoFactorRequired: true as const,
            twoFactorToken: r.twoFactorToken!
          });
        }
        this.tokenStorage.write({
          accessToken: r.accessToken!,
          accessExpiresInSeconds: r.expiresIn!
        });
        this.state.set({ status: 'anonymous' });
        return this.loadMe().pipe(map(() => ({ twoFactorRequired: false as const })));
      })
    );
  }

  loadMe() {
    return this.http.get<MeResponse>('/api/me').pipe(
      tap((me) => this.state.set({ status: 'authenticated', me })),
      catchError((err) => {
        this.state.set({
          status: 'error',
          message: err?.error?.message ?? 'Failed to load session'
        });
        return of(null);
      })
    );
  }

  verifyTwoFactor(twoFactorToken: string, code: string) {
    return this.http
      .post<LoginResponse>('/api/auth/2fa/verify', { twoFactorToken, code })
      .pipe(
        tap((r) =>
          this.tokenStorage.write({
            accessToken: r.accessToken!,
            accessExpiresInSeconds: r.expiresIn!
          })
        ),
        tap(() => this.state.set({ status: 'anonymous' })),
        switchMap(() => this.loadMe().pipe(map(() => void 0)))
      );
  }

  setup2fa() {
    return this.http.post<TwoFactorSetupResponse>('/api/2fa/setup', {});
  }

  confirm2fa(code: string) {
    return this.http.post<void>('/api/2fa/confirm', { code }).pipe(
      tap(() => {
        const current = this.me();
        if (current) {
          this.state.set({
            status: 'authenticated',
            me: { ...current, twoFactorEnabled: true }
          });
        }
      })
    );
  }

  disable2fa(code: string) {
    return this.http.post<void>('/api/2fa/disable', { code }).pipe(
      tap(() => {
        const current = this.me();
        if (current) {
          this.state.set({
            status: 'authenticated',
            me: { ...current, twoFactorEnabled: false }
          });
        }
      })
    );
  }

  /** Clears client session without calling the server (e.g. after failed token refresh). */
  clearLocalSession(): void {
    this.tokenStorage.clear();
    this.state.set({ status: 'anonymous' });
  }

  logout() {
    return this.http.post<void>('/api/auth/logout', {}).pipe(
      catchError(() => of(void 0)),
      tap(() => this.clearLocalSession())
    );
  }
}

