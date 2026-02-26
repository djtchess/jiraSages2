import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export const apiErrorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      console.error('[API ERROR]', {
        method: req.method,
        url: req.urlWithParams,
        status: error.status,
        message: error.message
      });
      return throwError(() => error);
    })
  );
