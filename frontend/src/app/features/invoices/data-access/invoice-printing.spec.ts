import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  IDEMPOTENCY_KEY,
  idempotencyInterceptor,
} from '../../../core/interceptors/idempotency.interceptor';
import { invoice, invoicePath } from '../invoice-test-data';
import { InvoicePrinting } from './invoice-printing';

describe('InvoicePrinting', () => {
  const printPath = `${invoicePath}/print`;
  const processing = { ...invoice, status: 'PROCESSING', allowedActions: [] };
  const closed = {
    ...processing,
    status: 'CLOSED',
    closedAt: '2026-11-20T09:01:00Z',
  };
  let http: HttpTestingController;
  let printing: InvoicePrinting;
  const openSnackbar = vi.fn();

  beforeEach(() => {
    vi.useFakeTimers();
    openSnackbar.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([idempotencyInterceptor])),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: { open: openSnackbar } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    printing = TestBed.inject(InvoicePrinting);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  function acceptPrint(): string | null {
    const request = http.expectOne({ url: printPath, method: 'POST' });
    expect(request.request.body).toBeNull();
    const key = request.request.headers.get(IDEMPOTENCY_KEY);
    expect(key).toMatch(/^[a-f\d]{8}(?:-[a-f\d]{4}){3}-[a-f\d]{12}$/i);
    request.flush(
      { id: invoice.id, status: 'PROCESSING' },
      { status: 202, statusText: 'Accepted' },
    );
    return key;
  }

  it('starts polling only after acceptance, checks every 1.5 s and announces success once', () => {
    const next = vi.fn();
    const complete = vi.fn();
    printing.start(invoice.id).subscribe({ next, complete });
    vi.advanceTimersByTime(500);
    http.expectNone(invoicePath);
    acceptPrint();
    expect(next).toHaveBeenLastCalledWith({ status: 'processing' });

    vi.advanceTimersByTime(0);
    http.expectOne({ url: invoicePath, method: 'GET' }).flush(processing);
    vi.advanceTimersByTime(1499);
    http.expectNone(invoicePath);
    vi.advanceTimersByTime(1);
    http.expectOne(invoicePath).flush(closed);

    expect(next).toHaveBeenLastCalledWith({ status: 'finished', invoice: closed });
    expect(openSnackbar).toHaveBeenCalledExactlyOnceWith(
      'Nota fiscal fechada com sucesso.',
      'Fechar',
      {
        duration: 3000,
      },
    );
    expect(complete).toHaveBeenCalledOnce();
    vi.advanceTimersByTime(30_000);
    http.expectNone(invoicePath);
  });

  it('returns the rejection and uses a fresh key for a new print that clears it', () => {
    const next = vi.fn();
    const rejected = { ...invoice, lastFailureReason: 'Saldo insuficiente para PAR-M6.' };
    printing.start(invoice.id).subscribe(next);
    const rejectedKey = acceptPrint();
    vi.advanceTimersByTime(0);
    http.expectOne(invoicePath).flush(rejected);
    expect(next).toHaveBeenLastCalledWith({ status: 'finished', invoice: rejected });
    expect(openSnackbar).not.toHaveBeenCalled();

    printing.start(invoice.id).subscribe(next);
    expect(acceptPrint()).not.toBe(rejectedKey);
    vi.advanceTimersByTime(0);
    http.expectOne(invoicePath).flush(closed);
    expect(next).toHaveBeenLastCalledWith({ status: 'finished', invoice: closed });
  });

  it('stops at 30 s even when the API continues returning PROCESSING', () => {
    const next = vi.fn();
    const complete = vi.fn();
    printing.start(invoice.id).subscribe({ next, complete });
    acceptPrint();

    for (let poll = 0; poll < 20; poll++) {
      vi.advanceTimersByTime(poll === 0 ? 0 : 1500);
      http.expectOne(invoicePath).flush(processing);
    }
    vi.advanceTimersByTime(1499);
    expect(next).toHaveBeenCalledExactlyOnceWith({ status: 'processing' });
    vi.advanceTimersByTime(1);
    expect(next).toHaveBeenLastCalledWith({ status: 'timed-out' });
    expect(complete).toHaveBeenCalledOnce();
    vi.advanceTimersByTime(3000);
    http.expectNone(invoicePath);
    expect(openSnackbar).not.toHaveBeenCalled();
  });
});
