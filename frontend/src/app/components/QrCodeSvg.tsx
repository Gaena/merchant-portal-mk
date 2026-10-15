import React, { useMemo } from 'react';
import { Box } from '@mui/material';
import qrcode from 'qrcode-generator';

// QR-код строится в браузере (Р-127): адрес ссылки, секрет второго фактора (Р-141) на сервер не уходят ещё раз.
// Цвета всегда чёрный на белом и белое поле в 4 модуля, как требует стандарт: инверсный или прижатый к краю
// код многие сканеры не читают.
export const QR_QUIET_ZONE = 4;

export function buildQrMatrix(text: string): boolean[][] {
  const qr = qrcode(0, 'M');
  qr.addData(text);
  qr.make();
  const n = qr.getModuleCount();
  return Array.from({ length: n }, (_, row) => Array.from({ length: n }, (_, col) => qr.isDark(row, col)));
}

interface QrCodeSvgProps {
  matrix: boolean[][];
  size: number;
  ariaLabel: string;
}

export const QrCodeSvg: React.FC<QrCodeSvgProps> = ({ matrix, size, ariaLabel }) => {
  const side = matrix.length + 2 * QR_QUIET_ZONE;
  const path = useMemo(() => matrix
    .flatMap((cells, row) => cells.map((dark, col) => (dark ? `M${col + QR_QUIET_ZONE},${row + QR_QUIET_ZONE}h1v1h-1z` : '')))
    .join(''), [matrix]);

  return (
    <Box
      component="svg"
      viewBox={`0 0 ${side} ${side}`}
      shapeRendering="crispEdges"
      role="img"
      aria-label={ariaLabel}
      sx={{ width: size, height: size, display: 'block', border: '1px solid', borderColor: 'divider', borderRadius: 1 }}
    >
      <rect width={side} height={side} fill="#ffffff" />
      <path d={path} fill="#000000" />
    </Box>
  );
};
