package az.millikart.pbl.dto;

import az.millikart.pbl.provider.dto.TerminalCheckResult;

/**
 * Что показать администратору после проверки терминала.
 *
 * `outcome` — один из четырёх исходов, и фронт различает их все. `message` — слова провайдера,
 * когда они есть: при отказе они и есть объяснение, почему терминал не принимает оплаты.
 */
public record TerminalCheckResponse(
        TerminalCheckResult.Outcome outcome,
        String providerErrorCode,
        String message
) {

    public static TerminalCheckResponse of(TerminalCheckResult result) {
        return new TerminalCheckResponse(result.outcome(), result.providerErrorCode(), result.providerMessage());
    }
}
