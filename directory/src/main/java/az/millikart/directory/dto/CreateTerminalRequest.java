package az.millikart.directory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Заведение терминала.
 *
 * Название и логин мы не придумываем: у провайдера один терминал — это один мерчант, и оба поля
 * принадлежат ему. Администратор выбирает строку из слепка (`merchantRid`) и вводит только
 * пароль, а название с логином подставляются оттуда. Поэтому они и не `@NotBlank`: с `merchantRid`
 * они не нужны вовсе, без него — обязательны, и это проверяет сервис, где видно оба поля сразу.
 */
public record CreateTerminalRequest(
        @NotNull(message = "Terminal ID is required")
        Integer id,

        /** Игнорируется, когда передан `merchantRid`: название приходит от провайдера. */
        String name,

        /** Игнорируется, когда передан `merchantRid`: логин приходит от провайдера. */
        String login,

        @NotBlank(message = "Terminal password is required")
        String password,

        @NotBlank(message = "Company ID is required")
        String companyId,

        /**
         * Терминал провайдера, за которым встаёт этот. Необязателен: терминалы заводились
         * и до синхронизации, и такие остаются без привязки — сверка их не касается.
         */
        String merchantRid
) {
}
