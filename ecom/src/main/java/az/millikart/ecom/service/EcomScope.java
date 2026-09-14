package az.millikart.ecom.service;

import java.util.List;

// Чьи платежи видит пользователь. logins — логины терминалов у шлюза, по ним выписка находит
// мерчантов (запрос выписки от 15.09.2026); пустой — выписка пустая. merchantRids — терминалы
// провайдера за теми же терминалами: только для фильтра, у заведённых без привязки его нет.
public record EcomScope(List<String> logins, List<String> merchantRids) {
}
