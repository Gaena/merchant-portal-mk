import React from 'react';
import { StatusPage } from '../components/StatusPage';
import { useLanguage } from '../context/LanguageContext';

export const NotFoundPage: React.FC = () => {
  const { tObj } = useLanguage();
  return <StatusPage code="404" title={tObj.errors.notFoundTitle} text={tObj.errors.notFoundText} />;
};
