package ee.cooppank.loanapprovalprocess.controller;

import ee.cooppank.loanapprovalprocess.entity.LoanApplication;
import ee.cooppank.loanapprovalprocess.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/loan")
public class LoanController {

    private final LoanService  loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping("/apply")
    public LoanApplication apply(@Valid @RequestBody LoanApplication application) {
        LoanApplication saved = loanService.saveApplication(application);
        return loanService.processApplication(saved);
    }

    @GetMapping("/{id}")
    public LoanApplication getApplication(@PathVariable UUID id) {
        return loanService.getApplication(id);
    }
}
