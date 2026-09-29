package az.millikart.pbl.controller;

import az.millikart.common.web.ClientIp;
import az.millikart.common.web.TrustedProxies;
import az.millikart.pbl.dto.PaymentReceiptView;
import az.millikart.pbl.provider.ProviderPayloads;
import az.millikart.pbl.service.OpenLinkService;
import az.millikart.pbl.service.PaymentLinkService;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/api/v1/payment-links")
public class OpenLinkController {

    private static final Logger log = LoggerFactory.getLogger(OpenLinkController.class);

    private final OpenLinkService openLinkService;
    private final PaymentLinkService paymentLinkService;
    private final TrustedProxies trustedProxies;

    public OpenLinkController(OpenLinkService openLinkService,
                              PaymentLinkService paymentLinkService,
                              TrustedProxies trustedProxies) {
        this.openLinkService = openLinkService;
        this.paymentLinkService = paymentLinkService;
        this.trustedProxies = trustedProxies;
    }

    @GetMapping("/{id}/open")
    public ResponseEntity<Void> open(@PathVariable UUID id, HttpServletRequest request) {
        // Только ClientIp (P2-10): адрес ложится в transactions.client_ip, и выбирать его плательщику нельзя.
        String clientIp = ClientIp.resolve(request, trustedProxies.addresses());
        String userAgent = request.getHeader("User-Agent");
        String redirectUrl = openLinkService.openAndBuildRedirect(id, clientIp, userAgent);
        // P0-9: в query редиректа — пароль заказа, в лог только адрес.
        log.debug("Redirecting the payer to {}", ProviderPayloads.urlForLog(redirectUrl));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    // Только по tx — нашему случайному ridByMerchant. Query ID, PASSWORD и STATUS от провайдера
    // подконтрольны атакующему и намеренно не объявлены: ни в метод, ни в лог они не попадают.
    @GetMapping("/redirect/{tx}")
    public String redirectPage(@PathVariable("tx") String tx, Model model) {
        PaymentReceiptView receipt = null;
        try {
            UUID ridByMerchant = UUID.fromString(tx);
            try {
                receipt = paymentLinkService.refreshByRidByMerchant(ridByMerchant).orElse(null);
            } catch (OptimisticLockingFailureException e) {
                // Другой плательщик той же ссылки поднял её версию; конфликт всплывает на коммите, мимо
                // catch сервиса: один повтор вместо JSON 409.
                log.info("Return page refresh for a payment raced with another update of its link; retrying once");
                receipt = paymentLinkService.refreshByRidByMerchant(ridByMerchant).orElse(null);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Payment return page requested with a malformed transaction reference");
        }

        // null рисует нейтральное «сведения недоступны»: неизвестная или битая ссылка не должна
        // выдавать, существует транзакция или нет.
        model.addAttribute("receipt", receipt);
        return "redirect";
    }
}
