package az.millikart.directory.dto;

import jakarta.validation.constraints.NotBlank;

// Заведение терминала. Номер выдаёт база (Р-81), вводить его не нужно. Название и логин принадлежат
// провайдеру: администратор выбирает строку справочника (merchantRid) и вводит пароль, а название с
// логином подставляются оттуда (Р-67). Поэтому они не @NotBlank: без merchantRid их требует сервис.
public record CreateTerminalRequest(
        // Игнорируется, когда передан merchantRid: название приходит от провайдера.
        String name,

        // Игнорируется, когда передан merchantRid: логин приходит от провайдера.
        String login,

        @NotBlank(message = "Terminal password is required")
        String password,

        @NotBlank(message = "Company ID is required")
        String companyId,

        // Терминал провайдера за нашим. Необязателен: терминалы без привязки сверка не касается.
        String merchantRid
) {
}
