package az.millikart.pbl.service;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.domain.UsageType;
import az.millikart.pbl.dto.CompleteDmsRequest;
import az.millikart.pbl.dto.CreatePaymentLinkRequest;
import az.millikart.pbl.dto.CustomerDto;
import az.millikart.pbl.dto.PagedResponse;
import az.millikart.pbl.dto.PaymentLinkResponse;
import az.millikart.pbl.dto.PaymentLinkSummaryResponse;
import az.millikart.pbl.dto.RefundRequest;
import az.millikart.pbl.dto.RefundResponse;
import az.millikart.pbl.dto.TransactionResponse;
import az.millikart.pbl.dto.UpdatePaymentLinkRequest;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.exception.ResourceNotFoundException;

import az.millikart.pbl.provider.AcquiringClient;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import az.millikart.common.security.UserPrincipal;

@Service
public class PaymentLinkService {

    private static final Logger log = LoggerFactory.getLogger(PaymentLinkService.class);

    private final PaymentLinkRepository paymentLinkRepository;
    private final TransactionRepository transactionRepository;
    private final TerminalRepository terminalRepository;
    private final AcquiringClient acquiringClient;
    private final PaymentLinkMapper mapper;
    private final TransactionTemplate txTemplate;
    private final String baseUrl;

    public PaymentLinkService(PaymentLinkRepository paymentLinkRepository,
                               TransactionRepository transactionRepository,
                               TerminalRepository terminalRepository,
                               AcquiringClient acquiringClient,
                               PaymentLinkMapper mapper,
                               PlatformTransactionManager transactionManager,
                               @Value("${pbl.base-url}") String baseUrl) {
        this.paymentLinkRepository = paymentLinkRepository;
        this.transactionRepository = transactionRepository;
        this.terminalRepository = terminalRepository;
        this.acquiringClient = acquiringClient;
        this.mapper = mapper;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.baseUrl = baseUrl;
    }

    public PaymentLinkResponse create(CreatePaymentLinkRequest request, UserPrincipal principal) {
        String userId = UserPrincipal.getUserId(principal);
        String userRole = UserPrincipal.getRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to create payment link: merchantOrderId={}, terminal={}, amount={}, currency={}, userId={}, role={}, companyId={}",
                request.merchantOrderId(), request.terminal(), request.amount(), request.currency(), userId, userRole, companyId);

        // Validate user's role and company access to the terminal
        validateAccess(request.terminal(), userRole, companyId, List.of("SYSTEM_ADMIN", "COMPANY_HEAD", "COMPANY_MANAGER", "COMPANY_EMPLOYEE"));

        CustomerDto customer = request.customer();
        String providerRef = "RID-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        PaymentLink link = PaymentLink.builder()
                .providerReference(providerRef)
                .merchantOrderId(request.merchantOrderId())
                .terminalId(request.terminal())
                .amount(request.amount())
                .currency(request.currency())
                .description(request.description())
                .customerName(customer != null ? customer.fullName() : null)
                .customerEmail(customer != null ? customer.email() : null)
                .customerPhone(customer != null ? customer.phone() : null)
                .paymentType(request.paymentType())
                .usageType(request.usageType())
                .maxPayments(request.usageType() == UsageType.MULTIPLE ? request.maxPayments() : null)
                .currentPaymentsCount(0) // legacy field, kept for entity mapping but count is computed dynamically
                .status(PaymentLinkStatus.ACTIVE)
                .metadata(request.metadata())
                .build();

        PaymentLink saved = txTemplate.execute(status -> paymentLinkRepository.saveAndFlush(link));
        log.info("Payment link created successfully with ID: {} and provider reference: {}", saved.getId(), providerRef);
        return mapper.toResponse(saved, 0);
    }

    @Transactional
    public PaymentLinkResponse update(UUID id, UpdatePaymentLinkRequest request, UserPrincipal principal) {
        String userId = UserPrincipal.getUserId(principal);
        String userRole = UserPrincipal.getRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to update payment link: id={}, userId={}, role={}, companyId={}", id, userId, userRole, companyId);
        PaymentLink link = findLinkOrThrow(id);
        
        // Validate user's role and company access to the terminal
        validateAccess(link.getTerminalId(), userRole, companyId, List.of("SYSTEM_ADMIN", "COMPANY_HEAD", "COMPANY_MANAGER", "COMPANY_EMPLOYEE"));

        if (request.amount() != null) {
            log.debug("Updating payment link amount to: {}", request.amount());
            link.setAmount(request.amount());
        }
        if (request.description() != null) {
            log.debug("Updating payment link description");
            link.setDescription(request.description());
        }
        if (request.customer() != null) {
            log.debug("Updating customer details");
            CustomerDto customer = request.customer();
            if (customer.fullName() != null) {
                link.setCustomerName(customer.fullName());
            }
            if (customer.email() != null) {
                link.setCustomerEmail(customer.email());
            }
            if (customer.phone() != null) {
                link.setCustomerPhone(customer.phone());
            }
        }
        if (request.expiresAt() != null) {
            log.debug("Updating expiration timestamp to: {}", request.expiresAt());
            link.setExpiresAt(request.expiresAt());
        }
        if (request.maxPayments() != null) {
            if (link.getUsageType() != UsageType.MULTIPLE) {
                log.warn("Cannot set maxPayments on SINGLE use link {}", id);
                throw new BusinessException("maxPayments can only be set when usageType is MULTIPLE");
            }
            log.debug("Updating maxPayments to: {}", request.maxPayments());
            link.setMaxPayments(request.maxPayments());
        }
        if (request.metadata() != null) {
            log.debug("Updating custom metadata");
            link.setMetadata(request.metadata());
        }
        if (request.status() != null) {
            if (request.status() != PaymentLinkStatus.ACTIVE && request.status() != PaymentLinkStatus.CANCELED) {
                log.warn("Invalid target status update: {}", request.status());
                throw new BusinessException("status can only be updated to ACTIVE or CANCELED");
            }
            log.info("Changing status of link {} to {}", id, request.status());
            link.setStatus(request.status());
        }

        PaymentLink saved = paymentLinkRepository.save(link);
        long successCount = transactionRepository.countByLinkIdAndStatus(id, TransactionStatus.SUCCESS);
        return mapper.toResponse(saved, (int) successCount);
    }

    @Transactional(readOnly = true)
    public PaymentLinkResponse get(UUID id, UserPrincipal principal) {
        String userId = UserPrincipal.getUserId(principal);
        String userRole = UserPrincipal.getRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to fetch payment link details: id={}, userId={}, role={}, companyId={}", id, userId, userRole, companyId);
        PaymentLink link = findLinkOrThrow(id);
        
        // Validate user's role and company access to the terminal
        validateAccess(link.getTerminalId(), userRole, companyId, List.of("SYSTEM_ADMIN", "COMPANY_HEAD", "COMPANY_MANAGER", "COMPANY_EMPLOYEE", "AUDITOR"));
        
        long successCount = transactionRepository.countByLinkIdAndStatus(id, TransactionStatus.SUCCESS);
        return mapper.toResponse(link, (int) successCount);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentLinkSummaryResponse> list(Integer terminal, PaymentLinkStatus status, Pageable pageable, UserPrincipal principal) {
        String userId = UserPrincipal.getUserId(principal);
        String userRole = UserPrincipal.getRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to list payment links: terminal={}, status={}, userId={}, role={}, companyId={}", terminal, status, userId, userRole, companyId);
        List<String> allowedRoles = List.of("SYSTEM_ADMIN", "COMPANY_HEAD", "COMPANY_MANAGER", "COMPANY_EMPLOYEE", "AUDITOR");
        if (!allowedRoles.contains(userRole)) {
            log.warn("Access denied. Role {} is not authorized to list payment links.", userRole);
            throw new InvalidStateException("Access denied: role " + userRole + " is not authorized for this action");
        }

        boolean isSystemAdmin = "SYSTEM_ADMIN".equals(userRole);
        List<Integer> allowedTerminals = Collections.emptyList();

        if (!isSystemAdmin) {
            if (companyId == null || companyId.isBlank()) {
                log.warn("Missing companyId claim for non-admin user: {}", userId);
                Page<PaymentLink> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
                return PagedResponse.of(emptyPage, Collections.emptyList());
            }
            allowedTerminals = terminalRepository.findAllByCompanyId(companyId).stream()
                    .map(Terminal::getId)
                    .toList();

            log.debug("Found allowed terminals for company {}: {}", companyId, allowedTerminals);
            if (allowedTerminals.isEmpty()) {
                Page<PaymentLink> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
                return PagedResponse.of(emptyPage, Collections.emptyList());
            }
        }

        if (terminal != null) {
            validateAccess(terminal, userRole, companyId, allowedRoles);
        }

        Page<PaymentLink> page = paymentLinkRepository.search(terminal, allowedTerminals, isSystemAdmin, status, pageable);
        List<PaymentLinkSummaryResponse> content = page.getContent().stream()
                .map(mapper::toSummary)
                .toList();
        return PagedResponse.of(page, content);
    }

    @Transactional
    public PaymentLinkResponse completeDms(UUID transactionId, CompleteDmsRequest request, UserPrincipal principal) {
        String userId = UserPrincipal.getUserId(principal);
        String userRole = UserPrincipal.getRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to complete DMS: transactionId={}, amount={}, userId={}, role={}, companyId={}", transactionId, request.amount(), userId, userRole, companyId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> {
                    log.warn("Transaction not found for DMS complete: {}", transactionId);
                    return new ResourceNotFoundException("Transaction not found: " + transactionId);
                });
        PaymentLink link = transaction.getLink();

        // Validate user's role and company access to this transaction's terminal
        validateAccess(link.getTerminalId(), userRole, companyId, List.of("SYSTEM_ADMIN", "COMPANY_HEAD", "COMPANY_MANAGER", "COMPANY_EMPLOYEE"));

        if (transaction.getStatus() != TransactionStatus.AUTHORIZED
                && transaction.getStatus() != TransactionStatus.PENDING
                && transaction.getStatus() != TransactionStatus.SUCCESS) {
            log.warn("Cannot complete DMS. Transaction {} is in status: {}", transactionId, transaction.getStatus());
            throw new BusinessException("Transaction is in status " + transaction.getStatus() + ". Only PENDING, AUTHORIZED or SUCCESS transactions can be completed.");
        }

        Terminal terminal = terminalRepository.findById(link.getTerminalId())
                .orElseThrow(() -> {
                    log.error("Terminal {} configuration missing for transaction {}", link.getTerminalId(), transactionId);
                    return new BusinessException("Terminal configuration not found");
                });

        log.info("Sending DMS Clearing capture request to provider for providerOrderId: {}, amount: {}", transaction.getProviderOrderId(), request.amount());
        
        // Call provider API to clear the transaction
        Map<String, Object> providerResp = acquiringClient.completeDms(
                transaction.getProviderOrderId(),
                transaction.getProviderPassword(),
                terminal.getLogin(),
                terminal.getPassword(),
                request.amount()
        );

        if (providerResp != null) {
            Map<String, Object> mergedResponse = new HashMap<>();
            if (transaction.getProviderResponse() != null) {
                mergedResponse.putAll(transaction.getProviderResponse());
            }
            mergedResponse.putAll(providerResp);
            transaction.setProviderResponse(mergedResponse);
        }

        transaction.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(transaction);
        log.info("Transaction {} captured successfully and transitioned to SUCCESS.", transactionId);

        // Check if limit is reached to mark link as completed
        long successCount = transactionRepository.countByLinkIdAndStatus(link.getId(), TransactionStatus.SUCCESS);
        link.setCurrentPaymentsCount((int) successCount);
        if (link.getUsageType() == UsageType.SINGLE) {
            log.info("Single-use link {} successfully completed.", link.getId());
            link.setStatus(PaymentLinkStatus.COMPLETED);
        } else if (link.getUsageType() == UsageType.MULTIPLE && link.getMaxPayments() != null && successCount >= link.getMaxPayments()) {
            log.info("Multi-use link {} reached max payments limit. Transitioned to COMPLETED.", link.getId());
            link.setStatus(PaymentLinkStatus.COMPLETED);
        }
        PaymentLink savedLink = paymentLinkRepository.save(link);

        return mapper.toResponse(savedLink, (int) successCount);
    }

    @Transactional
    public RefundResponse refund(UUID transactionId, RefundRequest request, UserPrincipal principal) {
        String userId = UserPrincipal.getUserId(principal);
        String userRole = UserPrincipal.getRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to refund transaction: transactionId={}, amount={}, userId={}, role={}, companyId={}", transactionId, request.amount(), userId, userRole, companyId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> {
                    log.warn("Transaction not found for refund: {}", transactionId);
                    return new ResourceNotFoundException("Transaction not found: " + transactionId);
                });
        PaymentLink link = transaction.getLink();

        // Validate user's role and company access (Refund is restricted to SYSTEM_ADMIN, COMPANY_HEAD, COMPANY_MANAGER)
        validateAccess(link.getTerminalId(), userRole, companyId, List.of("SYSTEM_ADMIN", "COMPANY_HEAD", "COMPANY_MANAGER"));

        if (transaction.getStatus() != TransactionStatus.SUCCESS && transaction.getStatus() != TransactionStatus.PARTIALLY_REFUNDED) {
            log.warn("Cannot refund transaction. Current status: {}", transaction.getStatus());
            throw new BusinessException("Only successful or partially refunded transactions can be refunded");
        }

        BigDecimal newRefundedAmount = transaction.getRefundedAmount().add(request.amount());
        if (newRefundedAmount.compareTo(transaction.getAmount()) > 0) {
            log.warn("Refund amount {} exceeds remaining transaction amount.", request.amount());
            throw new BusinessException("Refund amount exceeds the original transaction amount");
        }

        Terminal terminal = terminalRepository.findById(link.getTerminalId())
                .orElseThrow(() -> {
                    log.error("Terminal {} configuration missing for transaction {}", link.getTerminalId(), transactionId);
                    return new BusinessException("Terminal configuration not found");
                });

        log.info("Sending refund request to provider for providerOrderId: {}, amount: {}", transaction.getProviderOrderId(), request.amount());

        // Call provider API to initiate refund
        Map<String, Object> result = acquiringClient.refund(transaction.getProviderOrderId(), transaction.getProviderPassword(), terminal.getLogin(), terminal.getPassword(), request.amount());
        String refundId = (String) result.getOrDefault("refundId", "REF-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        transaction.setRefundedAmount(newRefundedAmount);
        if (newRefundedAmount.compareTo(transaction.getAmount()) == 0) {
            log.info("Transaction {} fully refunded.", transactionId);
            transaction.setStatus(TransactionStatus.REFUNDED);
        } else {
            log.info("Transaction {} partially refunded. Total refunded: {}", transactionId, newRefundedAmount);
            transaction.setStatus(TransactionStatus.PARTIALLY_REFUNDED);
        }
        transactionRepository.save(transaction);

        return new RefundResponse(
                transaction.getId(),
                transaction.getStatus().name(),
                request.amount(),
                refundId
        );
    }

    @Transactional
    public TransactionResponse checkAndStatusUpdate(String identifier) {
        log.info("Checking transaction status for identifier: {}", identifier);
        Transaction tx = null;

        try {
            UUID uuid = UUID.fromString(identifier);
            tx = transactionRepository.findById(uuid).orElse(null);
        } catch (IllegalArgumentException ignored) {}

        if (tx == null) {
            tx = transactionRepository.findByProviderOrderId(identifier)
                    .orElseThrow(() -> {
                        log.warn("Transaction not found for identifier: {}", identifier);
                        return new ResourceNotFoundException("Transaction not found: " + identifier);
                    });
        }

        if (tx.getStatus() == TransactionStatus.SUCCESS || tx.getStatus() == TransactionStatus.FAILED
                || tx.getStatus() == TransactionStatus.REFUNDED || tx.getStatus() == TransactionStatus.PARTIALLY_REFUNDED) {
            log.debug("Transaction {} already in terminal state: {}", identifier, tx.getStatus());
            return mapToTransactionResponse(tx);
        }

        PaymentLink link = tx.getLink();
        Terminal terminal = terminalRepository.findById(link.getTerminalId())
                .orElseThrow(() -> {
                    log.error("Terminal {} configuration missing for transaction status check {}", link.getTerminalId(), identifier);
                    return new BusinessException("Terminal configuration not found");
                });

        Map<String, Object> orderDetails = acquiringClient.getOrderStatus(tx.getProviderOrderId(), tx.getProviderPassword(), terminal.getLogin(), terminal.getPassword());
        if (orderDetails != null) {
            String providerStatus = (String) orderDetails.get("status");
            log.info("Provider order status check result: identifier={}, providerStatus={}", identifier, providerStatus);

            if ("FullyPaid".equals(providerStatus) || "Cleared".equals(providerStatus)) {
                tx.setStatus(TransactionStatus.SUCCESS);
                // Check if link needs to be completed
                long successCount = transactionRepository.countByLinkIdAndStatus(link.getId(), TransactionStatus.SUCCESS) + 1; // including current successful one
                link.setCurrentPaymentsCount((int) successCount);
                if (link.getUsageType() == UsageType.SINGLE) {
                    log.info("Single-use link {} completed due to status transition to SUCCESS.", link.getId());
                    link.setStatus(PaymentLinkStatus.COMPLETED);
                } else if (link.getUsageType() == UsageType.MULTIPLE && link.getMaxPayments() != null && successCount >= link.getMaxPayments()) {
                    log.info("Multi-use link {} completed due to status transition to SUCCESS and limit reached.", link.getId());
                    link.setStatus(PaymentLinkStatus.COMPLETED);
                }
                paymentLinkRepository.save(link);
            } else if ("Authorized".equals(providerStatus)) {
                tx.setStatus(TransactionStatus.AUTHORIZED);
            } else if ("Failed".equals(providerStatus) || "Canceled".equals(providerStatus) || "Declined".equals(providerStatus) || "Rejected".equals(providerStatus)) {
                tx.setStatus(TransactionStatus.FAILED);
            }

            tx.setProviderResponse(orderDetails);
            tx = transactionRepository.save(tx);
            log.info("Transaction {} updated status to: {}", tx.getId(), tx.getStatus());
        }

        return mapToTransactionResponse(tx);
    }

    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> listTransactions(Pageable pageable, UserPrincipal principal) {
        Page<Transaction> page = transactionRepository.findAll(pageable);
        List<TransactionResponse> content = page.getContent().stream()
                .map(this::mapToTransactionResponse)
                .toList();

        return new PagedResponse<>(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSize(),
                page.getNumber()
        );
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByLinkId(UUID linkId, UserPrincipal principal) {
        PaymentLink link = findLinkOrThrow(linkId);
        validateAccess(link.getTerminalId(), principal.getRole(), principal.getCompanyId(), List.of("SYSTEM_ADMIN", "MERCHANT_ADMIN", "MERCHANT_USER"));
        return transactionRepository.findByLinkIdOrderByCreatedAtDesc(linkId).stream()
                .map(this::mapToTransactionResponse)
                .toList();
    }

    private TransactionResponse mapToTransactionResponse(Transaction tx) {
        Map<String, Object> resp = tx.getProviderResponse();
        String cardNumberMasked = resp != null && resp.get("cardNumberMasked") != null ? String.valueOf(resp.get("cardNumberMasked")) : null;
        String rrn = resp != null && resp.get("rrn") != null ? String.valueOf(resp.get("rrn")) : null;
        String approvalCode = resp != null && resp.get("approvalCode") != null ? String.valueOf(resp.get("approvalCode")) : null;

        return new TransactionResponse(
                tx.getId(),
                tx.getLink() != null ? tx.getLink().getId() : null,
                tx.getStatus().name(),
                tx.getAmount(),
                tx.getRefundedAmount(),
                tx.getLink() != null ? tx.getLink().getCurrency() : "AZN",
                tx.getLink() != null ? tx.getLink().getDescription() : null,
                tx.getLink() != null ? tx.getLink().getMerchantOrderId() : null,
                tx.getLink() != null && tx.getLink().getPaymentType() != null ? tx.getLink().getPaymentType().name() : "SMS",
                tx.getLink() != null ? tx.getLink().getTerminalId() : null,
                tx.getMerchantRid() != null ? tx.getMerchantRid().toString() : null,
                cardNumberMasked,
                rrn,
                approvalCode,
                tx.getCreatedAt(),
                tx.getLink() != null ? tx.getLink().getCustomerName() : null,
                tx.getLink() != null ? tx.getLink().getCustomerEmail() : null,
                tx.getLink() != null ? tx.getLink().getCustomerPhone() : null,
                tx.getClientIp(),
                tx.getUserAgent(),
                tx.getProviderOrderId()
        );
    }

    private void validateAccess(Integer terminalId, String userRole, String companyId, List<String> allowedRoles) {
        log.debug("Validating terminal access: terminalId={}, role={}, companyId={}, allowedRoles={}", terminalId, userRole, companyId, allowedRoles);
        if (!allowedRoles.contains(userRole)) {
            log.warn("Access denied. Role {} is not in allowed roles: {}", userRole, allowedRoles);
            throw new InvalidStateException("Access denied: role " + userRole + " is not authorized for this action");
        }
        Terminal terminal = terminalRepository.findById(terminalId)
                .orElseThrow(() -> {
                    log.warn("Terminal not found: {}", terminalId);
                    return new ResourceNotFoundException("Terminal not found: " + terminalId);
                });
        if ("SYSTEM_ADMIN".equals(userRole)) {
            log.debug("Access granted. User is SYSTEM_ADMIN.");
            return;
        }
        if (companyId == null || !companyId.equals(terminal.getCompanyId())) {
            log.warn("Access denied. User companyId {} does not match terminal companyId {}", companyId, terminal.getCompanyId());
            throw new InvalidStateException("Access denied to terminal: " + terminalId);
        }
        log.debug("Access granted for company: {}", companyId);
    }

    private PaymentLink findLinkOrThrow(UUID id) {
        return paymentLinkRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Payment link not found: {}", id);
                    return new ResourceNotFoundException("Payment link not found: " + id);
                });
    }
}
