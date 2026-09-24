package az.millikart.directory.dto;

import java.util.List;

// Свободный логин мультимерчанта для формы компании (Р-95): login — целиком, с префиксом, как его
// примет POST /companies; merchants — названия его активных мерчантов.
public record ProviderLoginOption(String login, List<String> merchants) {
}
