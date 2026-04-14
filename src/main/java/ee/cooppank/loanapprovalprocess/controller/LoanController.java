package ee.cooppank.loanapprovalprocess.controller;

import ee.cooppank.loanapprovalprocess.entity.LoanApplication;
import ee.cooppank.loanapprovalprocess.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loan")
public class LoanController {

    private final LoanService  loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping("/apply")
    public LoanApplication apply(@Valid @RequestBody LoanApplication application) {
        // Service kontrollib isikukoodi. Kui on vale, viskab Service vea.
        loanService.validateApplication(application);

        // Kui siia maale jõutakse, on kõik korras
        return application;
    }
}
