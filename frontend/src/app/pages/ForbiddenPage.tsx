import React from 'react';
import { StatusPage } from '../components/StatusPage';
import { useLanguage } from '../context/LanguageContext';

export const ForbiddenPage: React.FC = () => {
  const { tObj } = useLanguage();
  return <StatusPage code="403" title={tObj.errors.forbiddenTitle} text={tObj.errors.forbiddenText} />;
};
