package com.clydehomu.disputes.transaction.api;

import com.clydehomu.disputes.auth.application.AuthenticatedUser;
import com.clydehomu.disputes.shared.security.CustomerOnly;
import com.clydehomu.disputes.shared.web.PageResponse;
import com.clydehomu.disputes.transaction.application.TransactionQuery;
import com.clydehomu.disputes.transaction.application.TransactionQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@CustomerOnly
public class TransactionController {

    private final TransactionQueryService transactions;

    public TransactionController(TransactionQueryService transactions) {
        this.transactions = transactions;
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public PageResponse<TransactionSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "q", required = false) @Size(min = 1, max = 100) String text,
            @RequestParam(required = false) Boolean disputed,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        TransactionQuery query = new TransactionQuery(from, to, text, Boolean.TRUE.equals(disputed), page, size);
        return PageResponse.of(transactions.list(user.getId(), accountId, query), TransactionSummaryResponse::from);
    }

    @GetMapping("/transactions/{id}")
    public TransactionDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return TransactionDetailResponse.from(transactions.get(user.getId(), id));
    }
}
