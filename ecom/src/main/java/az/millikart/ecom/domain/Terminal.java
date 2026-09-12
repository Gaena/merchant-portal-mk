package az.millikart.ecom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Наш терминал — ровно те его поля, которые нужны выписке, и только на чтение.
 *
 * Таблица принадлежит `directory`, там она заводится и правится; здесь из неё берут две вещи:
 * чьей компании терминал и какой терминал провайдера за ним стоит. Сеттеров у полей нет
 * намеренно — писать в чужую таблицу этот сервис не должен, а `pbl` держит у себя такую же
 * read-only копию по той же причине.
 *
 * Пароля и логина здесь нет: выписка читается не ими, а незаведённое поле не утечёт.
 */
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

    /** Терминал провайдера, за которым стоит наш. Пусто у заведённых до синхронизации. */
    @Column(name = "merchant_rid")
    private String merchantRid;
}
