import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl } from '@angular/forms';
import { readProblemDetail } from '../models/problem-detail';

export function setFormProblem(form: AbstractControl, error: unknown, fallback: string): string {
  const problem = error instanceof HttpErrorResponse ? readProblemDetail(error) : undefined;
  const unmapped: string[] = [];
  for (const { field, message } of problem?.errors ?? []) {
    const control = field ? form.get(field) : undefined;
    if (control?.enabled) {
      control.setErrors({ ...control.errors, server: message });
    } else {
      unmapped.push(message);
    }
  }

  if (unmapped.length) {
    return unmapped.join(' ');
  }
  return problem?.errors?.length ? '' : (problem?.detail ?? fallback);
}

export function fieldMessage(control: AbstractControl, fallback: string): string {
  const serverMessage: unknown = control.getError('server');
  if (typeof serverMessage === 'string') {
    return serverMessage;
  }
  return control.hasError('required') ? 'Campo obrigatório.' : fallback;
}

export class SubmissionKeys {
  private readonly keys = new Map<string, string>();

  forPayload(payload: object): string {
    const fingerprint = JSON.stringify(payload);
    const existing = this.keys.get(fingerprint);
    if (existing) {
      return existing;
    }

    const key = crypto.randomUUID();
    this.keys.set(fingerprint, key);
    return key;
  }

  forget(payload: object): void {
    this.keys.delete(JSON.stringify(payload));
  }
}
