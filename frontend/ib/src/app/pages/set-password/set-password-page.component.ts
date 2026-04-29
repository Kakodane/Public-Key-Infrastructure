import { Component, OnInit, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CaService } from '../../core/ca/ca.service';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const a = group.get('password')?.value;
  const b = group.get('confirm')?.value;
  return a && b && a !== b ? { mismatch: true } : null;
}

@Component({
  selector: 'app-set-password-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './set-password-page.component.html',
  styleUrl: './set-password-page.component.css'
})
export class SetPasswordPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly ca = inject(CaService);

  readonly token = signal<string | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly ok = signal(false);

  readonly form = new FormGroup(
    {
      password: new FormControl('', {
        nonNullable: true,
        validators: [
          Validators.required,
          Validators.minLength(8),
          Validators.maxLength(128),
          Validators.pattern(
            /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/
          )
        ]
      }),
      confirm: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required]
      })
    },
    { validators: passwordsMatch }
  );

  ngOnInit() {
    const t = this.route.snapshot.queryParamMap.get('token');
    this.token.set(t);
    if (!t) {
      this.error.set('Missing activation token. Use the link from your email.');
    }
  }

  submit() {
    this.error.set(null);
    const t = this.token();
    if (!t) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { password } = this.form.getRawValue();
    this.busy.set(true);
    this.ca.setPassword({ token: t, password }).subscribe({
      next: () => {
        this.ok.set(true);
        this.busy.set(false);
        setTimeout(() => this.router.navigateByUrl('/login'), 1500);
      },
      error: (err) => {
        this.error.set(extractError(err));
        this.busy.set(false);
      }
    });
  }
}

function extractError(err: any): string {
  if (err?.error?.errors?.length) {
    return err.error.errors.join(', ');
  }
  if (err?.status === 410) {
    return err?.error?.message ?? 'This activation link has expired.';
  }
  if (err?.status === 400) {
    return err?.error?.message ?? 'Invalid token or password.';
  }
  return err?.error?.message ?? err?.message ?? 'Failed to set password';
}
