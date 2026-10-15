import React, { useMemo } from 'react';
import { Button, Stack, Typography } from '@mui/material';
import { Download as DownloadIcon } from '@mui/icons-material';
import { useLanguage } from '../context/LanguageContext';
import { QR_QUIET_ZONE, QrCodeSvg, buildQrMatrix } from './QrCodeSvg';

// QR-код адреса платёжной ссылки строится в браузере из `url` ответа API — бэкенду он не нужен (Р-127).
const PNG_MODULE_PX = 16;

function downloadPng(matrix: boolean[][], fileName: string) {
  const side = (matrix.length + 2 * QR_QUIET_ZONE) * PNG_MODULE_PX;
  const canvas = document.createElement('canvas');
  canvas.width = side;
  canvas.height = side;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, side, side);
  ctx.fillStyle = '#000000';
  matrix.forEach((cells, row) => cells.forEach((dark, col) => {
    if (dark) {
      ctx.fillRect((col + QR_QUIET_ZONE) * PNG_MODULE_PX, (row + QR_QUIET_ZONE) * PNG_MODULE_PX, PNG_MODULE_PX, PNG_MODULE_PX);
    }
  }));
  const a = document.createElement('a');
  a.href = canvas.toDataURL('image/png');
  a.download = fileName;
  a.click();
}

interface LinkQrCodeProps {
  url: string;
  // Подпись файла — короткий код ссылки: по нему файл находят среди других.
  shortCode: string;
  size?: number;
}

export const LinkQrCode: React.FC<LinkQrCodeProps> = ({ url, shortCode, size = 200 }) => {
  const { tObj } = useLanguage();
  const matrix = useMemo(() => buildQrMatrix(url), [url]);
  const fileName = `payment-link-${shortCode.replace(/[^A-Za-z0-9_-]/g, '_')}.png`;

  return (
    <Stack spacing={1.5} sx={{ alignItems: 'center' }}>
      <QrCodeSvg matrix={matrix} size={size} ariaLabel={tObj.payByLink.qrCode} />
      <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'center' }}>
        {tObj.payByLink.qrHint}
      </Typography>
      <Button size="small" variant="outlined" startIcon={<DownloadIcon />} onClick={() => downloadPng(matrix, fileName)}>
        {tObj.payByLink.downloadQr}
      </Button>
    </Stack>
  );
};
