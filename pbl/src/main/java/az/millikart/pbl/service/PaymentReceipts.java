package az.millikart.pbl.service;

import az.millikart.pbl.config.ReceiptRequisites;
import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.dto.PaymentReceiptView;
import az.millikart.pbl.provider.ProviderOrderDetails;
import az.millikart.pbl.provider.ProviderOrderDetails.ReceiptFacts;
import az.millikart.pbl.repository.CompanyRequisitesRepository;
import az.millikart.pbl.repository.CompanyRequisitesRepository.CompanyRequisites;
import az.millikart.pbl.repository.TerminalRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Чек плательщика (Р-130): реквизиты провайдера из настроек, продавца — из компании терминала, операции —
// из ответа провайдера, сохранённого опросом. Зовётся внутри транзакции страницы возврата.
@Component
public class PaymentReceipts {

    private static final DateTimeFormatter PROVIDER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter RECEIPT_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final ReceiptRequisites requisites;
    private final TerminalRepository terminalRepository;
    private final CompanyRequisitesRepository companyRequisitesRepository;
    private final ZoneId zone;

    public PaymentReceipts(ReceiptRequisites requisites,
                           TerminalRepository terminalRepository,
                           CompanyRequisitesRepository companyRequisitesRepository,
                           @Value("${pbl.dashboard.zone}") String zoneId) {
        this.requisites = requisites;
        this.terminalRepository = terminalRepository;
        this.companyRequisitesRepository = companyRequisitesRepository;
        this.zone = ZoneId.of(zoneId);
    }

    public PaymentReceiptView of(Transaction tx) {
        PaymentLink link = tx.getLink();
        Terminal terminal = link != null ? terminalRepository.findById(link.getTerminalId()).orElse(null) : null;
        CompanyRequisites company = terminal != null
                ? companyRequisitesRepository.findByCompanyId(terminal.getCompanyId()).orElse(null)
                : null;
        ReceiptFacts facts = ProviderOrderDetails.receiptFacts(tx.getProviderResponse());
        return new PaymentReceiptView(
                state(tx.getStatus()),
                requisites.providerName(),
                requisites.providerTaxId(),
                company != null ? company.name() : null,
                company != null ? company.taxId() : null,
                terminal != null ? terminal.getName() : null,
                terminal != null ? terminal.getTerminalRid() : null,
                tx.getProviderOrderId(),
                facts.rrn(),
                facts.approvalCode(),
                operationTime(facts.operationTime(), tx),
                facts.cardBrand(),
                facts.cardLastFour(),
                // Списанное, а не авторизованное: после частичного списания чек — на списанную сумму (P0-8).
                tx.getCapturedAmount() != null ? tx.getCapturedAmount() : tx.getAmount(),
                link != null ? link.getCurrency() : null,
                link != null ? link.getProviderReference() : null,
                link != null ? link.getMerchantOrderId() : null,
                link != null ? link.getDescription() : null,
                link != null ? link.getCustomerName() : null,
                link != null ? link.getCustomerEmail() : null,
                link != null ? link.getCustomerPhone() : null);
    }

    static String state(TransactionStatus status) {
        return switch (status) {
            case SUCCESS, REFUNDED, PARTIALLY_REFUNDED -> "PAID";
            case AUTHORIZED -> "AUTHORIZED";
            case FAILED -> "FAILED";
            case PENDING -> "PENDING";
        };
    }

    // Время операции у провайдера; незнакомую форму — как пришла, а не догадкой. Без записи операции —
    // начало попытки в поясе отчёта: отстаёт от оплаты на минуты сессии, но это настоящий момент.
    private String operationTime(String providerTime, Transaction tx) {
        if (providerTime != null) {
            try {
                return LocalDateTime.parse(providerTime, PROVIDER_TIME).format(RECEIPT_TIME);
            } catch (DateTimeParseException e) {
                return providerTime;
            }
        }
        return tx.getCreatedAt() != null ? tx.getCreatedAt().atZone(zone).format(RECEIPT_TIME) : null;
    }
}
