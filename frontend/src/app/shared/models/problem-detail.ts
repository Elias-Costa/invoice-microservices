import { HttpErrorResponse } from '@angular/common/http';

export interface FieldViolation {
  field: string | null;
  message: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  traceId?: string;
  timestamp?: string;
  errors?: FieldViolation[];
}

export function readProblemDetail(error: HttpErrorResponse): ProblemDetail | undefined {
  const contentType = error.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase();
  const body: unknown = error.error;
  if (contentType !== 'application/problem+json' || !isObject(body)) {
    return undefined;
  }

  return {
    type: readText(body['type']),
    title: readText(body['title']),
    status: error.status,
    detail: readText(body['detail']),
    instance: readText(body['instance']),
    traceId: readText(body['traceId']) ?? error.headers.get('X-Trace-Id') ?? undefined,
    timestamp: readText(body['timestamp']),
    errors: Array.isArray(body['errors']) ? body['errors'].filter(isFieldViolation) : undefined,
  };
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readText(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value : undefined;
}

function isFieldViolation(value: unknown): value is FieldViolation {
  return (
    isObject(value) &&
    (typeof value['field'] === 'string' || value['field'] === null) &&
    typeof value['message'] === 'string'
  );
}
