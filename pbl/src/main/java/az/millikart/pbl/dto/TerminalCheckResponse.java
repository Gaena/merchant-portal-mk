package az.millikart.pbl.dto;

import az.millikart.pbl.provider.dto.TerminalCheckResult;

// message — слова провайдера, когда они есть: при отказе это и есть объяснение для администратора.
public record TerminalCheckResponse(
        TerminalCheckResult.Outcome outcome,
        String providerErrorCode,
        String message
) {

    public static TerminalCheckResponse of(TerminalCheckResult result) {
        return new TerminalCheckResponse(result.outcome(), result.providerErrorCode(), result.providerMessage());
    }
}
