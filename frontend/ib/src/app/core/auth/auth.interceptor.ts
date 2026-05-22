import { HttpErrorResponse, HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injector } from '@angular/core';
import { Router } from '@angular/router';
import {
  catchError,
  finalize,
  map,
  Observable,
  shareReplay,
  switchMap,
  throwError
} from 'rxjs';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';
import { TokenResponse } from './auth.types';

let refreshInFlight$: Observable<void> | null = null;

/** Endpoints that must not send a stored access token. */
function skipAccessTokenHeader(url: string): boolean {
  return (
    url.startsWith('/api/auth/login') ||
    url.startsWith('/api/auth/refresh') ||
    url.startsWith('/api/auth/2fa/verify')
  );
}

function endClientSession(injector: Injector): void {
  injector.get(TokenStorageService).clear();
  try {
    injector.get(AuthService).clearLocalSession();
  } catch {
    // AuthService may not be available during early bootstrap
  }
  void injector.get(Router).navigateByUrl('/login');
}

function refreshAccessToken(
  http: HttpClient,
  tokenStorage: TokenStorageService,
  injector: Injector
) {
  refreshInFlight$ ??= http.post<TokenResponse>('/api/auth/refresh', {}).pipe(
    map((r) => {
      tokenStorage.write({
        accessToken: r.accessToken,
        accessExpiresInSeconds: r.expiresIn
      });
    }),
    catchError((err) => {
      endClientSession(injector);
      return throwError(() => err);
    }),
    shareReplay({ bufferSize: 1, refCount: false }),
    finalize(() => {
      refreshInFlight$ = null;
    })
  );
  return refreshInFlight$;
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const injector = inject(Injector);
  const tokenStorage = inject(TokenStorageService);
  const http = inject(HttpClient);

  const tokens = tokenStorage.read();
  const attachToken = tokens?.accessToken && !skipAccessTokenHeader(req.url);

  const reqWithAuth = attachToken
    ? req.clone({ setHeaders: { Authorization: `Bearer ${tokens.accessToken}` } })
    : req;

  return next(reqWithAuth).pipe(
    catchError((err) => {
      if (!(err instanceof HttpErrorResponse)) return throwError(() => err);
      if (err.status !== 401) return throwError(() => err);
      if (skipAccessTokenHeader(req.url)) return throwError(() => err);

      return refreshAccessToken(http, tokenStorage, injector).pipe(
        switchMap(() => {
          const nextTokens = tokenStorage.read();
          if (!nextTokens?.accessToken) {
            endClientSession(injector);
            return throwError(() => err);
          }
          return next(
            req.clone({ setHeaders: { Authorization: `Bearer ${nextTokens.accessToken}` } })
          );
        }),
        catchError((refreshErr) => {
          endClientSession(injector);
          return throwError(() => refreshErr);
        })
      );
    })
  );
};
