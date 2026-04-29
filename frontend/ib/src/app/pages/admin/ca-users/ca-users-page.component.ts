import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminService } from '../../../core/admin/admin.service';
import { GetOrgsResponse } from '../../../core/admin/admin.types';

type CreatedCa = { id: number; email: string };

@Component({
  selector: 'app-ca-users-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './ca-users-page.component.html',
  styleUrl: './ca-users-page.component.css'
})
export class CaUsersPageComponent implements OnInit {
  private readonly admin = inject(AdminService);

  readonly orgs = signal<GetOrgsResponse[]>([]);
  readonly orgsLoading = signal(false);
  readonly createdCas = signal<CreatedCa[]>([]);

  readonly orgBusy = signal(false);
  readonly orgError = signal<string | null>(null);
  readonly orgOk = signal<string | null>(null);

  readonly caBusy = signal(false);
  readonly caError = signal<string | null>(null);
  readonly caOk = signal<string | null>(null);

  readonly orgForm = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(2), Validators.maxLength(128)]
    })
  });

  readonly caForm = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email]
    }),
    firstName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    lastName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    orgId: new FormControl<number | null>(null, {
      validators: [Validators.required]
    })
  });

  ngOnInit() {
    this.loadOrgs();
  }

  private loadOrgs() {
    this.orgsLoading.set(true);
    this.admin.getOrganizations().subscribe({
      next: (orgs) => this.orgs.set(orgs),
      error: () => this.orgs.set([]),
      complete: () => this.orgsLoading.set(false)
    });
  }

  submitOrg() {
    this.orgError.set(null);
    this.orgOk.set(null);
    if (this.orgForm.invalid) {
      this.orgForm.markAllAsTouched();
      return;
    }
    const { name } = this.orgForm.getRawValue();
    this.orgBusy.set(true);
    this.admin.createOrganization({ name }).subscribe({
      next: (org) => {
        this.orgs.update((prev) => [...prev, org]);
        this.orgOk.set(`Organization "${org.name}" created (id ${org.id}).`);
        this.orgForm.reset({ name: '' });
        this.caForm.patchValue({ orgId: org.id });
      },
      error: (err) => this.orgError.set(extractError(err)),
      complete: () => this.orgBusy.set(false)
    });
  }

  submitCa() {
    this.caError.set(null);
    this.caOk.set(null);
    if (this.caForm.invalid) {
      this.caForm.markAllAsTouched();
      return;
    }
    const v = this.caForm.getRawValue();
    if (v.orgId == null) {
      this.caError.set('Pick an organization.');
      return;
    }
    this.caBusy.set(true);
    this.admin
      .createCaUser({
        email: v.email,
        firstName: v.firstName,
        lastName: v.lastName,
        orgId: Number(v.orgId)
      })
      .subscribe({
        next: (res) => {
          this.createdCas.update((prev) => [...prev, { id: res.id, email: v.email }]);
          this.caOk.set(
            `CA user created (id ${res.id}). Activation email has been dispatched.`
          );
          this.caForm.reset({
            email: '',
            firstName: '',
            lastName: '',
            orgId: v.orgId
          });
        },
        error: (err) => this.caError.set(extractError(err)),
        complete: () => this.caBusy.set(false)
      });
  }
}

function extractError(err: any): string {
  if (err?.error?.errors?.length) {
    return err.error.errors.join(', ');
  }
  return err?.error?.message ?? err?.message ?? 'Request failed';
}
