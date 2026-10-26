package com.clydehomu.disputes.dispute.api;

import com.clydehomu.disputes.auth.application.AuthenticatedUser;
import com.clydehomu.disputes.dispute.application.DisputeQuery;
import com.clydehomu.disputes.dispute.application.DisputeService;
import com.clydehomu.disputes.dispute.application.DisputeView;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.shared.security.CustomerOnly;
import com.clydehomu.disputes.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DisputeController {

    private final DisputeService disputes;

    public DisputeController(DisputeService disputes) {
        this.disputes = disputes;
    }

    /** Any signed-in user may read the catalogue; it holds no customer data. */
    @GetMapping("/dispute-reasons")
    public List<ReasonCategoryResponse> reasons() {
        return ReasonCategoryResponse.catalogue();
    }

    @CustomerOnly
    @PostMapping("/disputes")
    public ResponseEntity<DisputeDetailResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateDisputeRequest request) {
        DisputeView created = disputes.create(user.getId(), request.toCommand());
        URI location = URI.create("/api/v1/disputes/" + created.dispute().getId());
        return ResponseEntity.created(location).body(DisputeDetailResponse.from(created));
    }

    @CustomerOnly
    @GetMapping("/disputes")
    public PageResponse<DisputeSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "status", required = false) List<DisputeStatus> statuses,
            @RequestParam(name = "q", required = false) @Size(min = 1, max = 100) String text,
            // The upper bound keeps page * size inside an int: a huge page would overflow the offset and fail as a 500.
            @RequestParam(defaultValue = "0") @Min(0) @Max(100_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Set<DisputeStatus> wanted = statuses == null || statuses.isEmpty() ? Set.of() : EnumSet.copyOf(statuses);
        return PageResponse.of(
                disputes.list(user.getId(), new DisputeQuery(wanted, text, page, size)), DisputeSummaryResponse::from);
    }

    @CustomerOnly
    @GetMapping("/disputes/{id}")
    public DisputeDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return DisputeDetailResponse.from(disputes.get(user.getId(), id));
    }

    @CustomerOnly
    @PostMapping("/disputes/{id}/withdraw")
    public DisputeDetailResponse withdraw(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return DisputeDetailResponse.from(disputes.withdraw(user.getId(), id));
    }

    @CustomerOnly
    @PostMapping("/disputes/{id}/responses")
    public DisputeDetailResponse respond(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody CustomerResponseRequest request) {
        return DisputeDetailResponse.from(disputes.respond(user.getId(), id, request.message()));
    }
}
