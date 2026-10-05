import React, { useMemo } from 'react';
import { Box, Button, Stack, Typography } from '@mui/material';
import { Download as DownloadIcon } from '@mui/icons-material';
import qrcode from 'qrcode-generator';
import { useLanguage } from '../context/LanguageContext';

// QR-код адреса платёжной ссылки строится в браузере из `url` ответа API — бэкенду он не нужен (Р-127).
// Цвета всегда чёрный на белом и белое поле в 4 модуля, как требует стандарт: инверсный или
// прижатый к краю код многие сканеры не читают.
const QUIET_ZONE = 4;
const PNG_MODULE_PX = 16;

function buildMatrix(text: string): boolean[][] {
  const qr = qrcode(0, 'M');
  qr.addData(text);
  qr.make();
  const n = qr.getModuleCount();
  return Array.from({ length: n }, (_, row) => Array.from({ length: n }, (_, col) => qr.isDark(row, col)));
}

function downloadPng(matrix: boolean[][], fileName: string) {
  const side = (matrix.length + 2 * QUIET_ZONE) * PNG_MODULE_PX;
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
      ctx.fillRect((col + QUIET_ZONE) * PNG_MODULE_PX, (row + QUIET_ZONE) * PNG_MODULE_PX, PNG_MODULE_PX, PNG_MODULE_PX);
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
  const matrix = useMemo(() => buildMatrix(url), [url]);
  const side = matrix.length + 2 * QUIET_ZONE;
  const path = useMemo(() => matrix
    .flatMap((cells, row) => cells.map((dark, col) => (dark ? `M${col + QUIET_ZONE},${row + QUIET_ZONE}h1v1h-1z` : '')))
    .join(''), [matrix]);
  const fileName = `payment-link-${shortCode.replace(/[^A-Za-z0-9_-]/g, '_')}.png`;

  return (
    <Stack spacing={1.5} sx={{ alignItems: 'center' }}>
      <Box
        component="svg"
        viewBox={`0 0 ${side} ${side}`}
        shapeRendering="crispEdges"
        role="img"
        aria-label={tObj.payByLink.qrCode}
        sx={{ width: size, height: size, display: 'block', border: '1px solid', borderColor: 'divider', borderRadius: 1 }}
      >
        <rect width={side} height={side} fill="#ffffff" />
        <path d={path} fill="#000000" />
      </Box>
      <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'center' }}>
        {tObj.payByLink.qrHint}
      </Typography>
      <Button size="small" variant="outlined" startIcon={<DownloadIcon />} onClick={() => downloadPng(matrix, fileName)}>
        {tObj.payByLink.downloadQr}
      </Button>
    </Stack>
  );
};
