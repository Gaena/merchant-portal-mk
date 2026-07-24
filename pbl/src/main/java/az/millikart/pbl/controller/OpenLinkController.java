package az.millikart.pbl.controller;

import az.millikart.pbl.service.OpenLinkService;
import az.millikart.pbl.service.PaymentLinkService;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/api/v1/payment-links")
public class OpenLinkController {

    private static final Logger log = LoggerFactory.getLogger(OpenLinkController.class);

    private final OpenLinkService openLinkService;
    private final PaymentLinkService paymentLinkService;

    public OpenLinkController(OpenLinkService openLinkService, PaymentLinkService paymentLinkService) {
        this.openLinkService = openLinkService;
        this.paymentLinkService = paymentLinkService;
    }

    @GetMapping("/{id}/open")
    public ResponseEntity<Void> open(@PathVariable UUID id, HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        log.info("REST request to open payment link ID: {}, clientIp: {}, userAgent: {}", id, clientIp, userAgent);
        String redirectUrl = openLinkService.openAndBuildRedirect(id, clientIp, userAgent);
        log.info("Redirecting customer to HPP URL: {}", redirectUrl);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/redirect/{tx}")
    public String redirectPage(@PathVariable("tx") String txUuid,
                               @RequestParam(value = "ID", required = false) String providerOrderId,
                               @RequestParam(value = "id", required = false) String providerOrderIdLower,
                               @RequestParam(value = "PASSWORD", required = false) String providerPassword,
                               @RequestParam(value = "STATUS", required = false) String providerStatus,
                               Model model) {
        String finalOrderId = providerOrderId != null ? providerOrderId : providerOrderIdLower;
        log.info("Customer redirected back from provider. ID: {}, PASSWORD: {}, STATUS: {}, tx: {}", 
                 finalOrderId, providerPassword != null ? "***" : "null", providerStatus, txUuid);

        // Fetch and update status in the background immediately
        if (finalOrderId != null) {
            try {
                paymentLinkService.checkAndStatusUpdate(finalOrderId);
            } catch (Exception e) {
                log.error("Failed to automatically update transaction status during redirect callback for ID: " + finalOrderId, e);
            }
        }

        model.addAttribute("providerOrderId", finalOrderId);
        return "redirect";
    }
}
