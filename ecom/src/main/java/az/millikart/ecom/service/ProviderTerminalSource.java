package az.millikart.ecom.service;

import java.util.List;

public interface ProviderTerminalSource {

    // Активные терминалы провайдера. Контракт: полный список или исключение. Частичный ответ
    // недопустим — отсутствие в списке читается как «выключен», и оборванная выборка погасила бы живые.
    List<ProviderTerminalRow> fetchActive();

    // rid — мерчант; terminalRid — номер терминала (terminal.rid), с ним pbl создаёт заказ (Р-96).
    record ProviderTerminalRow(String rid, String title, String login, String terminalRid) {
    }
}
