package az.millikart.common.money;

import az.millikart.common.security.Role;
import java.util.EnumSet;
import java.util.Set;

// Кто двигает деньги по операции — один набор на pbl и ecom (Р-124): заказ выписки и операция портала —
// одни и те же деньги, и права на них не должны разойтись.
public final class MoneyActionRoles {

    // Без COMPANY_EMPLOYEE: возврат двигает деньги обратно.
    public static final Set<Role> REFUND = EnumSet.of(Role.SYSTEM_ADMIN, Role.COMPANY_HEAD, Role.COMPANY_MANAGER);

    public static final Set<Role> CAPTURE =
            EnumSet.of(Role.SYSTEM_ADMIN, Role.COMPANY_HEAD, Role.COMPANY_MANAGER, Role.COMPANY_EMPLOYEE);

    // Разрешить неизвестный исход — только администратор, после сверки с провайдером (Р-123).
    public static final Set<Role> RESOLVE = EnumSet.of(Role.SYSTEM_ADMIN);

    private MoneyActionRoles() {
    }
}
