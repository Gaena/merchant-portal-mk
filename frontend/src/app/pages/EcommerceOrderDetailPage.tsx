import React from 'react';
import { useNavigate, useParams } from 'react-router';
import { Button } from '@mui/material';
import { ArrowBack as ArrowBackIcon } from '@mui/icons-material';
import { useLanguage } from '../context/LanguageContext';
import { EcomOrderDetails } from '../components/EcomOrderDetails';

// Заказ по прямой ссылке и из «последних заказов» главной. Из выписки он открывается панелью поверх неё: так
// фильтры и подгруженные строки выписки остаются на месте.
export const EcommerceOrderDetailPage: React.FC = () => {
  const { orderId = '' } = useParams();
  const navigate = useNavigate();
  const { tObj } = useLanguage();

  return (
    <EcomOrderDetails
      orderId={orderId}
      actions={
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/transactions/ecommerce')}>
          {tObj.ecommerce.detail.back}
        </Button>
      }
    />
  );
};
