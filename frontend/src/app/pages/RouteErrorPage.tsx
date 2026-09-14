import React from 'react';
import { isRouteErrorResponse, useRouteError } from 'react-router';
import { StatusPage } from '../components/StatusPage';
import { useLanguage } from '../context/LanguageContext';

/**
 * `errorElement` корневых маршрутов: исключение при рендере или загрузке страницы (в том числе
 * упавший `lazy()`-чанк после деплоя) показывает эту страницу вместо белого экрана.
 */
export const RouteErrorPage: React.FC = () => {
  const error = useRouteError();
  const { tObj } = useLanguage();

  if (isRouteErrorResponse(error)) {
    // Ошибка самого роутера (например, 404 без catch-all) — со статусом.
    const isNotFound = error.status === 404;
    return (
      <StatusPage
        code={String(error.status)}
        title={isNotFound ? tObj.errors.notFoundTitle : tObj.errors.unexpectedTitle}
        text={isNotFound ? tObj.errors.notFoundText : tObj.errors.unexpectedText}
        detail={error.statusText || undefined}
      />
    );
  }

  // Упавший `lazy()`-чанк — это старая вкладка после деплоя: адрес чанка пользователю ни к чему,
  // а «на главную» без перезагрузки привёл бы к той же ошибке. Перезагружаем страницу целиком.
  const chunkFailed = error instanceof Error && /dynamically imported module|Loading chunk|Importing a module script failed/i.test(error.message);
  const detail = error instanceof Error && !chunkFailed ? error.message : undefined;
  console.error('[router] unhandled error', error);
  return (
    <StatusPage
      code="!"
      title={tObj.errors.unexpectedTitle}
      text={tObj.errors.unexpectedText}
      detail={detail}
      onHome={chunkFailed ? () => window.location.assign('/') : undefined}
    />
  );
};
