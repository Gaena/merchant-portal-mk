package az.millikart.ecom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Наш терминал — только поля, нужные выписке, и только на чтение: таблица принадлежит directory,
// писать в неё отсюда нельзя, поэтому сеттеров нет. Пароля нет — выписке он не нужен.
@Entity
@Table(name = "terminals")
@Getter
@NoArgsConstructor
public class Terminal {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "company_id")
    private String companyId;

    // Логин терминала у шлюза — по нему выписка находит мерчанта (запрос выписки от 15.09.2026).
    @Column(name = "login")
    private String login;

    // Терминал провайдера за нашим; пусто у заведённых без привязки. Нужен фильтру выписки.
    @Column(name = "merchant_rid")
    private String merchantRid;
}
