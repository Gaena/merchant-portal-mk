import { useEffect } from 'react';
import { describeError, refreshSession } from '../api/client';
import { getLastTokenAt, isIdleExpired, KEEP_ALIVE_AFTER_MS, markActivity } from './session';

/**
 * Что считается действием пользователя. Запросы к API — нет: фоновая загрузка страницы не должна
 * продлевать простой.
 */
const ACTIVITY_EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart', 'scroll'] as const;
/** Отмечать чаще незачем: граница простоя — 15 минут. */
const ACTIVITY_THROTTLE_MS = 15 * 1000;
const IDLE_CHECK_INTERVAL_MS = 30 * 1000;

/**
 * Выход по простою (PCI DSS 8.2.8, Р-99). Пока пользователь работает, действие отмечается в общей для
 * вкладок отметке, а refresh-токен обновляется, если пара старше `KEEP_ALIVE_AFTER_MS`: иначе сервер
 * (20 минут без обновления) погасил бы сессию человеку, который читает страницу без запросов к API.
 * Раз в `IDLE_CHECK_INTERVAL_MS` и при возврате на вкладку (после сна ноутбука тоже) — проверка простоя.
 */
export const useIdleLogout = (active: boolean, onIdle: () => void): void => {
  useEffect(() => {
    if (!active) {
      return;
    }
    let lastMarked = 0;
    const onActivity = () => {
      const now = Date.now();
      if (now - lastMarked < ACTIVITY_THROTTLE_MS) {
        return;
      }
      lastMarked = now;
      markActivity(now);
      if (now - getLastTokenAt() > KEEP_ALIVE_AFTER_MS) {
        refreshSession().catch((error: unknown) => {
          console.warn('[auth] keep-alive refresh failed:', describeError(error));
        });
      }
    };
    const check = () => {
      if (isIdleExpired(Date.now())) {
        onIdle();
      }
    };

    ACTIVITY_EVENTS.forEach((event) => window.addEventListener(event, onActivity, { passive: true, capture: true }));
    document.addEventListener('visibilitychange', check);
    const timer = window.setInterval(check, IDLE_CHECK_INTERVAL_MS);
    return () => {
      ACTIVITY_EVENTS.forEach((event) => window.removeEventListener(event, onActivity, { capture: true }));
      document.removeEventListener('visibilitychange', check);
      window.clearInterval(timer);
    };
  }, [active, onIdle]);
};
