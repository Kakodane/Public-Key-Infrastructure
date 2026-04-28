import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, finalize, map, Observable, shareReplay, switchMap, throwError } from 'rxjs';
import { TokenStorageService } from './token-storage.service';
import { HttpClient } from '@angular/common/http';
import { TokenResponse } from './auth.types';

let refreshInFlight$: Observable<void> | null = null;

function refreshAccessToken(http: HttpClient, tokenStorage: TokenStorageService) {
  refreshInFlight$ ??= http
    .post<TokenResponse>('/api/auth/refresh', {})
    .pipe(
      map((r) => {
        tokenStorage.write({
          accessToken: r.accessToken,
          accessExpiresInSeconds: r.expiresIn
        });
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
      finalize(() => {
        refreshInFlight$ = null;
      })
    );
  return refreshInFlight$;
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStorage = inject(TokenStorageService);
  const http = inject(HttpClient);

  const tokens = tokenStorage.read();
  const isAuthEndpoint = req.url.startsWith('/api/auth/login') || req.url.startsWith('/api/auth/refresh');

  const reqWithAuth =
    tokens?.accessToken && !isAuthEndpoint
      ? req.clone({ setHeaders: { Authorization: `Bearer ${tokens.accessToken}` } })
      : req;

  return next(reqWithAuth).pipe(
    catchError((err) => {
      if (!(err instanceof HttpErrorResponse)) return throwError(() => err);
      if (err.status !== 401) return throwError(() => err);
      if (isAuthEndpoint) return throwError(() => err);

      return refreshAccessToken(http, tokenStorage).pipe(
        switchMap(() => {
          const nextTokens = tokenStorage.read();
          if (!nextTokens?.accessToken) return throwError(() => err);
          return next(req.clone({ setHeaders: { Authorization: `Bearer ${nextTokens.accessToken}` } }));
        })
      );
    })
  );
};

