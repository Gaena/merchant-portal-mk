import { useEffect, useState } from 'react';

/**
 * Значение с задержкой (P3-1): строка поиска уходит в параметры запроса через 300 мс после
 * последнего нажатия, иначе каждая буква — отдельный запрос к серверу. Отмена устаревших
 * ответов — не здесь: её делает AbortController в эффекте загрузки каждой страницы.
 */
export function useDebounced<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}
