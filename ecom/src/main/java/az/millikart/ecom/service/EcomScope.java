package az.millikart.ecom.service;

import java.util.List;

// Чьи платежи видит пользователь: мерчанты за логинами мультимерчанта его компаний (Р-97). Пустой
// список — выписка пустая, и в шлюз за ней не ходят.
public record EcomScope(List<String> merchantRids) {
}
