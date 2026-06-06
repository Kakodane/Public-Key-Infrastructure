import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { AdminService } from '../../../core/admin/admin.service';
import {
  MasterKeyRotationJobStatus,
  MasterKeyStatusResponse
} from '../../../core/admin/admin.types';

@Component({
  selector: 'app-master-key-page',
  standalone: true,
  templateUrl: './master-key-page.component.html',
  styleUrl: './master-key-page.component.css'
})
export class MasterKeyPageComponent implements OnInit, OnDestroy {
  private readonly admin = inject(AdminService);

  readonly status = signal<MasterKeyStatusResponse | null>(null);
  readonly loading = signal(false);
  readonly rotateBusy = signal(false);
  readonly error = signal<string | null>(null);
  readonly rotateError = signal<string | null>(null);
  readonly rotateOk = signal<string | null>(null);

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  ngOnInit() {
    this.loadStatus();
  }

  ngOnDestroy() {
    this.stopPolling();
  }

  loadStatus() {
    this.error.set(null);
    this.loading.set(true);
    this.admin.getMasterKeyStatus().subscribe({
      next: (status) => {
        this.status.set(status);
        this.syncPolling(status.jobStatus);
      },
      error: (err) => {
        this.status.set(null);
        this.error.set(extractError(err));
        this.stopPolling();
      },
      complete: () => this.loading.set(false)
    });
  }

  rotateNow() {
    this.rotateError.set(null);
    this.rotateOk.set(null);
    this.rotateBusy.set(true);
    this.admin.triggerMasterKeyRotation().subscribe({
      next: (res) => {
        this.rotateOk.set(`Rotation job #${res.jobId} started.`);
        this.status.update((prev) =>
          prev
            ? { ...prev, jobStatus: res.status }
            : {
                currentVersion: 0,
                createdAt: '',
                lastRotationAt: null,
                jobStatus: res.status
              }
        );
        this.syncPolling(res.status);
        this.loadStatus();
      },
      error: (err) => this.rotateError.set(extractError(err)),
      complete: () => this.rotateBusy.set(false)
    });
  }

  formatDate(value: string | null | undefined) {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
  }

  labelJobStatus(value: MasterKeyRotationJobStatus | null | undefined) {
    if (!value) return 'No rotation jobs yet';
    return value;
  }

  isRotationRunning() {
    return this.status()?.jobStatus === 'RUNNING';
  }

  private syncPolling(jobStatus: MasterKeyRotationJobStatus | null | undefined) {
    if (jobStatus === 'RUNNING') {
      this.startPolling();
      return;
    }
    this.stopPolling();
  }

  private startPolling() {
    if (this.pollTimer) return;
    this.pollTimer = setInterval(() => this.loadStatusQuietly(), 3000);
  }

  private stopPolling() {
    if (!this.pollTimer) return;
    clearInterval(this.pollTimer);
    this.pollTimer = null;
  }

  private loadStatusQuietly() {
    this.admin.getMasterKeyStatus().subscribe({
      next: (status) => {
        this.status.set(status);
        this.syncPolling(status.jobStatus);
      },
      error: () => this.stopPolling()
    });
  }
}

function extractError(err: any): string {
  if (err?.error?.errors?.length) {
    return err.error.errors.join(', ');
  }
  return err?.error?.message ?? err?.message ?? 'Request failed';
}
