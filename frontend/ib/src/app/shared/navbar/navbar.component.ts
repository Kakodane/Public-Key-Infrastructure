import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import {
  ROLE_ADMIN,
  ROLE_CA_USER,
  ROLE_REGULAR_USER
} from '../../core/auth/role';

type NavLink = { path: string; label: string };

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css'
})
export class NavbarComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
 
  readonly links = computed<NavLink[]>(() => {
    const me = this.auth.me();
    if (!me) return [];
    switch (me.role) {
      case ROLE_ADMIN:
        return [
          { path: '/admin/ca-users', label: 'CA users' },
          { path: '/me', label: 'Session' }
        ];
      case ROLE_CA_USER:
        return [
          { path: '/ca/certificates', label: 'My org certificates' },
          { path: '/me', label: 'Session' }
        ];
      case ROLE_REGULAR_USER:
        return [
          { path: '/regular/certificates', label: 'My certificates' },
          { path: '/me', label: 'Session' }
        ];
      default:
        return [];
    }
  });

  readonly me = this.auth.me;

  logout() {
    this.auth.logout().subscribe({
      complete: () => this.router.navigateByUrl('/login')
    });
  }
}
