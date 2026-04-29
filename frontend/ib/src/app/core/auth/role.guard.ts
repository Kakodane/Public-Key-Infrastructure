import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { map, of } from 'rxjs';
import { AuthService } from './auth.service';

export function hasRoleGuard(allowed: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    const decide = (role: string | null | undefined) =>
      role && allowed.includes(role) ? true : router.parseUrl('/me');

    const me = auth.me();
    if (me) {
      return of(decide(me.role));
    }
    return auth.loadMe().pipe(map((res) => decide(res?.role)));
  };
}
