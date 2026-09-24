package az.millikart.ecom.service;

import java.util.List;

// Откуда берутся логины мультимерчантов провайдера со связями к мерчантам (Р-94).
public interface ProviderLoginSource {

    // Все логины ownerkind = MultiMerchantSys. Контракт: полный список или исключение — частичный
    // ответ заменил бы слепок, и живой логин компании перестал бы проходить проверку.
    List<ProviderLoginRow> fetchMultiMerchantLogins();

    // Строка выгрузки: логин без префикса владельца и одна его связь; у логина без связей
    // linkStatus, merchantRid и merchantTitle пусты.
    record ProviderLoginRow(String login, String loginStatus, String linkStatus, String merchantRid,
                            String merchantTitle) {
    }
}
