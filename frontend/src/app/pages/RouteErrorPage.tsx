import React from 'react';
import { isRouteErrorResponse, useRouteError } from 'react-router';
import { StatusPage } from '../components/StatusPage';
import { useLanguage } from '../context/LanguageContext';

/** `errorElement` корневых маршрутов: сбой рендера или загрузки чанка — эта страница, а не белый экран. */
export const RouteErrorPage: React.FC = () => {
  const error = useRouteError();
  const { tObj } = useLanguage();

  if (isRouteErrorResponse(error)) {
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

  // Упавший `lazy()`-чанк — старая вкладка после деплоя: переход без перезагрузки упадёт снова,
  // поэтому «на главную» перезагружает страницу, а адрес чанка не показывается.
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
