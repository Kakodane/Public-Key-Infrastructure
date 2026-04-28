import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, map, of, tap } from 'rxjs';
import { TokenStorageService } from './token-storage.service';
import { MeResponse, TokenResponse } from './auth.types';

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
    return this.http
      .post<TokenResponse>('/api/auth/login', { email, password })
      .pipe(
        tap((r) =>
          this.tokenStorage.write({
            accessToken: r.accessToken,
            accessExpiresInSeconds: r.expiresIn
          })
        ),
        tap(() => this.state.set({ status: 'anonymous' })),
        map(() => void 0),
        tap(() => this.loadMe().subscribe())
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

  logout() {
    return this.http.post<void>('/api/auth/logout', {}).pipe(
      catchError(() => of(void 0)),
      tap(() => {
        this.tokenStorage.clear();
        this.state.set({ status: 'anonymous' });
      })
    );
  }
}

