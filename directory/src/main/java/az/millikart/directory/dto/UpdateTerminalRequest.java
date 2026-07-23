package az.millikart.directory.dto;

public record UpdateTerminalRequest(
        String name,
        String login,
        String password,
        String companyId
) {
}
