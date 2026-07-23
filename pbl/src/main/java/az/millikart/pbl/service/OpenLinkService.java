package az.millikart.pbl.service;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.domain.UsageType;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.exception.ResourceNotFoundException;

import az.millikart.pbl.provider.AcquiringClient;
import az.millikart.pbl.provider.dto.EcomCreateOrderResponse;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OpenLinkService {

    private static final Logger log = LoggerFactory.getLogger(OpenLinkService.class);

    private final AcquiringClient acquiringClient;
    private final PaymentLinkRepository paymentLinkRepository;
    private final TransactionRepository transactionRepository;
    private final TerminalRepository terminalRepository;
    private final TransactionTemplate txTemplate;
    private final String baseUrl;

    public OpenLinkService(AcquiringClient acquiringClient,
                           PaymentLinkRepository paymentLinkRepository,
                           TransactionRepository transactionRepository,
                           TerminalRepository terminalRepository,
                           TransactionTemplate txTemplate,
                           @Value("${pbl.base-url}") String baseUrl) {
        this.acquiringClient = acquiringClient;
        this.paymentLinkRepository = paymentLinkRepository;
        this.transactionRepository = transactionRepository;
        this.terminalRepository = terminalRepository;
        this.txTemplate = txTemplate;
        this.baseUrl = baseUrl;
    }

    public String openAndBuildRedirect(UUID id) {
        log.info("Opening payment link session for ID: {}", id);

        // Phase 1: Validate Link status, expiry, and usage limits
        PaymentLink link = txTemplate.execute(status -> {
            PaymentLink l = findLinkOrThrow(id);

            if (l.getStatus() == PaymentLinkStatus.CANCELED
                    || l.getStatus() == PaymentLinkStatus.COMPLETED) {
                log.warn("Payment link {} is not available in status: {}", id, l.getStatus());
                throw new InvalidStateException("Payment link is not available for payment (status: " + l.getStatus() + ")");
            }

            if (l.getExpiresAt() != null && l.getExpiresAt().isBefore(Instant.now())) {
                log.info("Payment link {} has expired, changing status to EXPIRED", id);
                l.setStatus(PaymentLinkStatus.EXPIRED);
                paymentLinkRepository.save(l);
                throw new InvalidStateException("Payment link has expired");
            }
            if (l.getStatus() == PaymentLinkStatus.EXPIRED) {
                log.warn("Payment link {} has expired", id);
                throw new InvalidStateException("Payment link has expired");
            }

            long currentPaymentsCount = transactionRepository.countByLinkIdAndStatus(id, TransactionStatus.SUCCESS);

            if (l.getUsageType() == UsageType.SINGLE && currentPaymentsCount > 0) {
                log.warn("Single-use payment link {} was already successfully paid", id);
                throw new InvalidStateException("Single-use payment link has already been used");
            }
            if (l.getUsageType() == UsageType.MULTIPLE && l.getMaxPayments() != null && currentPaymentsCount >= l.getMaxPayments()) {
                log.info("Payment link {} has reached max payments limit: {}, setting status to COMPLETED", id, l.getMaxPayments());
                l.setStatus(PaymentLinkStatus.COMPLETED);
                paymentLinkRepository.save(l);
                throw new InvalidStateException("Payment link has reached its usage limit");
            }

            return l;
        });

        // Phase 2: Check for existing active transactions and handle timeout
        Optional<Transaction> activeTxOpt = transactionRepository
                .findFirstByLinkIdAndStatusInOrderByCreatedAtDesc(id, List.of(TransactionStatus.PENDING, TransactionStatus.AUTHORIZED));

        if (activeTxOpt.isPresent()) {
            Transaction activeTx = activeTxOpt.get();
            // 10 minutes checkout session timeout
            if (activeTx.getCreatedAt().isBefore(Instant.now().minusSeconds(600))) {
                log.info("Active transaction {} for link {} timed out (older than 10 mins). Changing status to FAILED.", activeTx.getId(), id);
                txTemplate.executeWithoutResult(status -> {
                    activeTx.setStatus(TransactionStatus.FAILED);
                    transactionRepository.save(activeTx);
                });
            } else {
                // If it's a fresh/active transaction, reuse the HPP URL
                log.info("Found fresh active transaction {} for link {}. Reusing existing redirect HPP URL.", activeTx.getId(), id);
                Map<String, Object> resp = activeTx.getProviderResponse();
                if (resp != null && resp.containsKey("hppUrl")) {
                    String hppUrl = (String) resp.get("hppUrl");
                    Object orderId = resp.get("id");
                    Object password = resp.get("password");
                    return hppUrl + "?id=" + orderId + "&password=" + password;
                }
            }
        }

        // Phase 3: Register a new order with the acquiring provider
        UUID merchantRid = UUID.randomUUID();
        Terminal terminal = terminalRepository.findById(link.getTerminalId())
                .orElseThrow(() -> {
                    log.error("Terminal {} configuration is missing for link {}", link.getTerminalId(), id);
                    return new BusinessException("Terminal configuration is not configured");
                });

        log.info("Registering new order at provider for link: {}, terminal: {}, merchantRid: {}", id, terminal.getId(), merchantRid);

        // Append the transaction UUID (merchantRid) as a query parameter for tracking
        String hppRedirectUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/v1/payment-links/redirect/{tx}")
                .buildAndExpand(merchantRid.toString())
                .toUriString();

        log.debug("Using redirect URL for provider: {}", hppRedirectUrl);

        EcomCreateOrderResponse response = acquiringClient.createEcomOrder(link, terminal.getLogin(), terminal.getPassword(), merchantRid, hppRedirectUrl);
        if (response == null || response.order() == null) {
            log.error("Failed to register order at provider for merchantRid: {}", merchantRid);
            throw new BusinessException("Failed to register order with provider");
        }

        log.info("Order registered at provider. ProviderOrderId: {}, redirecting user to HPP.", response.order().id());

        // Phase 4: Persist the new Transaction in PENDING status
        txTemplate.executeWithoutResult(status -> {
            Transaction transaction = Transaction.builder()
                    .link(link)
                    .merchantRid(merchantRid)
                    .providerOrderId(String.valueOf(response.order().id()))
                    .providerPassword(response.order().password())
                    .amount(link.getAmount())
                    .status(TransactionStatus.PENDING)
                    .providerResponse(Map.of(
                            "hppUrl", response.order().hppUrl(),
                            "id", response.order().id(),
                            "status", response.order().status(),
                            "password", response.order().password()
                    ))
                    .build();
            transactionRepository.save(transaction);
            log.debug("Persisted new PENDING transaction: {} for merchantRid: {}", transaction.getId(), merchantRid);
        });

        return response.order().hppUrl() + "?id=" + response.order().id() + "&password=" + response.order().password();
    }

    private PaymentLink findLinkOrThrow(UUID id) {
        return paymentLinkRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Payment link not found for ID: {}", id);
                    return new ResourceNotFoundException("Payment link not found: " + id);
                });
    }
}
