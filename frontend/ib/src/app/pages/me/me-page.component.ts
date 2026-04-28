import { Component } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { TokenStorageService } from '../../core/auth/token-storage.service';

@Component({
  selector: 'app-me-page',
  standalone: true,
  imports: [JsonPipe, RouterLink],
  templateUrl: './me-page.component.html'
})
export class MePageComponent {
  constructor(
    readonly auth: AuthService,
    readonly tokenStorage: TokenStorageService,
    private readonly router: Router
  ) {}

  logout() {
    this.auth.logout().subscribe({
      complete: () => this.router.navigateByUrl('/login')
    });
  }
}

