import { useEffect, useState } from 'react';

/**
 * Строка поиска уходит в запрос после паузы в наборе, а не на каждую букву (P3-1). Устаревшие
 * ответы отменяет не он, а AbortController в эффекте загрузки страницы.
 */
export function useDebounced<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}
