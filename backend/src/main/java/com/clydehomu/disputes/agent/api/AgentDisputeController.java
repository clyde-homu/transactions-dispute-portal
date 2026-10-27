package com.clydehomu.disputes.agent.api;

import com.clydehomu.disputes.agent.application.AgentDisputeQuery;
import com.clydehomu.disputes.agent.application.AgentDisputeService;
import com.clydehomu.disputes.agent.domain.AgentQueue;
import com.clydehomu.disputes.agent.domain.AgentQueueSort;
import com.clydehomu.disputes.auth.application.AuthenticatedUser;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.shared.security.AgentOnly;
import com.clydehomu.disputes.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The back-office dispute queue. Every operation is for agents only; customers get 403. */
@RestController
@RequestMapping("/api/v1/agent/disputes")
@AgentOnly
public class AgentDisputeController {

    private final AgentDisputeService disputes;

    public AgentDisputeController(AgentDisputeService disputes) {
        this.disputes = disputes;
    }

    @GetMapping
    public PageResponse<AgentDisputeSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "UNASSIGNED") AgentQueue queue,
            @RequestParam(name = "status", required = false) List<DisputeStatus> statuses,
            @RequestParam(name = "q", required = false) @Size(min = 1, max = 100) String text,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "DUE_AT") AgentQueueSort sort,
            // The upper bound keeps page * size inside an int: a huge page would overflow the offset and fail as a 500.
            @RequestParam(defaultValue = "0") @Min(0) @Max(100_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Set<DisputeStatus> wanted = statuses == null || statuses.isEmpty() ? Set.of() : EnumSet.copyOf(statuses);
        return PageResponse.of(
                disputes.list(user.getId(), new AgentDisputeQuery(queue, wanted, text, overdue, sort, page, size)),
                AgentDisputeSummaryResponse::from);
    }

    @GetMapping("/{id}")
    public AgentDisputeDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return AgentDisputeDetailResponse.from(disputes.get(user.getId(), id));
    }

    @PostMapping("/{id}/claim")
    public AgentDisputeDetailResponse claim(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return AgentDisputeDetailResponse.from(disputes.claim(user.getId(), id));
    }

    @PostMapping("/{id}/request-info")
    public AgentDisputeDetailResponse requestInfo(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody RequestInfoRequest request) {
        return AgentDisputeDetailResponse.from(disputes.requestInfo(user.getId(), id, request.message()));
    }

    @PostMapping("/{id}/resolve")
    public AgentDisputeDetailResponse resolve(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody ResolveDisputeRequest request) {
        return AgentDisputeDetailResponse.from(disputes.resolve(
                user.getId(), id, request.outcome(), request.customerMessage(), request.internalNote()));
    }
}
