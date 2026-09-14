package az.millikart.ecom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Наш терминал — только поля, нужные выписке, и только на чтение: таблица принадлежит directory,
// писать в неё отсюда нельзя, поэтому сеттеров нет. Логина и пароля нет — выписке они не нужны.
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

    // Терминал провайдера за нашим; пусто у заведённых без привязки.
    @Column(name = "merchant_rid")
    private String merchantRid;
}
