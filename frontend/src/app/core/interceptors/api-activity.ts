import { computed, Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ApiActivity {
  private readonly pending = signal(0);
  private readonly networkError = signal(false);

  readonly loading = computed(() => this.pending() > 0);
  readonly disconnected = this.networkError.asReadonly();

  startRequest(): void {
    this.pending.update((count) => count + 1);
  }

  finishRequest(): void {
    this.pending.update((count) => count - 1);
  }

  setDisconnected(disconnected: boolean): void {
    this.networkError.set(disconnected);
  }
}
