package az.millikart.ecom.service;

import java.util.List;
import java.util.stream.Stream;

// Тип оплаты — по операциям заказа, а не по колонке типа: её нет в SQL провайдера (Р-87). Результат и
// voidkind не важны: отклонённая попытка и реверсал покупки тип заказа не меняют.
public enum EcomPaymentType {
    SMS,
    DMS;

    public List<EcomOperationKind.TypePhase> signs() {
        return this == SMS
                ? EcomOperationKind.PURCHASE_SIGNS
                : Stream.concat(EcomOperationKind.AUTHORIZATION_SIGNS.stream(),
                        EcomOperationKind.CAPTURE_SIGNS.stream()).toList();
    }
}
