package az.millikart.ecom.service;

import java.util.List;
import java.util.stream.Stream;

// Тип оплаты заказа выписки — по его операциям, а не по колонке типа заказа: её нет в SQL провайдера,
// а одна несуществующая колонка роняет всю выписку (Р-87). DMS — у заказа есть холд или списание холда,
// SMS — оплата одним сообщением. Результат и voidkind не важны: отклонённая попытка и реверсал покупки
// тип заказа не меняют. Пары берутся из EcomOperationKind — того же словаря, по которому считаются деньги.
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
