package com.clydehomu.disputes.transaction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.clydehomu.disputes.account.application.AccountService;
import com.clydehomu.disputes.shared.error.NotFoundException;
import com.clydehomu.disputes.shared.error.ValidationFailedException;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.Channel;
import com.clydehomu.disputes.transaction.domain.EligibilityPolicy;
import com.clydehomu.disputes.transaction.domain.EligibilityReason;
import com.clydehomu.disputes.transaction.domain.LinkedDispute;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import com.clydehomu.disputes.transaction.infrastructure.BankTransactionRepository;
import com.clydehomu.disputes.transaction.infrastructure.LinkedDisputeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class TransactionQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID ACCOUNT = UUID.randomUUID();

    private final BankTransactionRepository transactions = mock(BankTransactionRepository.class);
    private final LinkedDisputeRepository linkedDisputes = mock(LinkedDisputeRepository.class);
    private final AccountService accounts = mock(AccountService.class);
    private TransactionQueryService service;

    @BeforeEach
    void setUp() {
        service = new TransactionQueryService(
                transactions, linkedDisputes, accounts, new EligibilityPolicy(120), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void anAccountThatIsNotTheCallersIsNotFoundAndNothingElseIsQueried() {
        doThrow(new NotFoundException("The requested resource was not found."))
                .when(accounts)
                .requireOwnedBy(CUSTOMER, ACCOUNT);

        assertThatThrownBy(() -> service.list(CUSTOMER, ACCOUNT, query(null, null, 0, 20)))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(transactions, linkedDisputes);
    }

    @Test
    void aFromDateAfterTheToDateIsRejectedBeforeAnyQuery() {
        assertThatThrownBy(() -> service.list(
                        CUSTOMER, ACCOUNT, query(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 1), 0, 20)))
                .isInstanceOfSatisfying(
                        ValidationFailedException.class,
                        e -> assertThat(e.getViolations())
                                .extracting(v -> v.field())
                                .containsExactly("from"));
        verifyNoInteractions(accounts, transactions);
    }

    @Test
    void aSingleDayRangeIsAllowed() {
        when(transactions.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());
        LocalDate day = LocalDate.of(2026, 10, 1);

        service.list(CUSTOMER, ACCOUNT, query(day, day, 0, 20));

        verify(transactions).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void pagesAreNewestFirstWithTheIdAsTieBreakAndTheRequestedSize() {
        when(transactions.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(CUSTOMER, ACCOUNT, query(null, null, 3, 50));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactions).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("postedAt"), Sort.Order.asc("id")));
    }

    @Test
    void disputesForAWholePageAreLoadedWithOneQueryAndTheLatestWins() {
        BankTransaction first = transaction(debit(TransactionStatus.SETTLED, 3));
        BankTransaction second = transaction(debit(TransactionStatus.SETTLED, 4));
        BankTransaction third = transaction(debit(TransactionStatus.SETTLED, 5));
        when(transactions.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second, third)));
        LinkedDispute newer = linked(first.getId(), "UNDER_REVIEW");
        LinkedDispute older = linked(first.getId(), "SUBMITTED");
        LinkedDispute other = linked(second.getId(), "SUBMITTED");
        when(linkedDisputes.findActiveByTransactionIds(anyCollection())).thenReturn(List.of(newer, older, other));

        Page<TransactionWithDispute> page = service.list(CUSTOMER, ACCOUNT, query(null, null, 0, 20));

        assertThat(page.getContent())
                .extracting(TransactionWithDispute::dispute)
                .containsExactly(newer, other, null);
        verify(linkedDisputes, times(1)).findActiveByTransactionIds(anyCollection());
    }

    @Test
    void anEmptyPageSkipsTheDisputeLookup() {
        when(transactions.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(CUSTOMER, ACCOUNT, query(null, null, 0, 20));

        verify(linkedDisputes, never()).findActiveByTransactionIds(anyCollection());
    }

    @Test
    void detailOfSomeoneElsesTransactionIsNotFound() {
        UUID id = UUID.randomUUID();
        when(transactions.findOwnedById(id, CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(CUSTOMER, id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void detailOfAnUndisputedRecentDebitIsEligible() {
        BankTransaction transaction = transaction(debit(TransactionStatus.SETTLED, 3));
        when(transactions.findOwnedById(transaction.getId(), CUSTOMER)).thenReturn(Optional.of(transaction));
        when(linkedDisputes.findActiveByTransactionIds(anyCollection())).thenReturn(List.of());

        TransactionDetailView view = service.get(CUSTOMER, transaction.getId());

        assertThat(view.eligibility().eligible()).isTrue();
        assertThat(view.transaction().dispute()).isNull();
    }

    @Test
    void detailOfADisputedTransactionIsNotEligibleAndCarriesTheDispute() {
        BankTransaction transaction = transaction(debit(TransactionStatus.SETTLED, 3));
        LinkedDispute dispute = linked(transaction.getId(), "SUBMITTED");
        when(transactions.findOwnedById(transaction.getId(), CUSTOMER)).thenReturn(Optional.of(transaction));
        when(linkedDisputes.findActiveByTransactionIds(anyCollection())).thenReturn(List.of(dispute));

        TransactionDetailView view = service.get(CUSTOMER, transaction.getId());

        assertThat(view.eligibility().reason()).isEqualTo(EligibilityReason.ALREADY_DISPUTED);
        assertThat(view.transaction().dispute()).isSameAs(dispute);
    }

    @Test
    void detailOfAnOldTransactionIsTooOld() {
        BankTransaction transaction = transaction(debit(TransactionStatus.SETTLED, 200));
        when(transactions.findOwnedById(transaction.getId(), CUSTOMER)).thenReturn(Optional.of(transaction));
        when(linkedDisputes.findActiveByTransactionIds(anyCollection())).thenReturn(List.of());

        assertThat(service.get(CUSTOMER, transaction.getId()).eligibility().reason())
                .isEqualTo(EligibilityReason.TOO_OLD);
    }

    private static TransactionQuery query(LocalDate from, LocalDate to, int page, int size) {
        return new TransactionQuery(from, to, null, false, page, size);
    }

    private static BankTransaction debit(TransactionStatus status, int daysAgo) {
        return new BankTransaction(
                null,
                "TXN-" + UUID.randomUUID().toString().substring(0, 8),
                NOW.minusSeconds(daysAgo * 86_400L),
                new BigDecimal("100.00"),
                "ZAR",
                TransactionDirection.DEBIT,
                status,
                "Fresh Market Midrand",
                "Groceries",
                Channel.CARD);
    }

    private static BankTransaction transaction(BankTransaction transaction) {
        ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
        return transaction;
    }

    private static LinkedDispute linked(UUID transactionId, String status) {
        LinkedDispute dispute = BeanUtils.instantiateClass(LinkedDispute.class);
        ReflectionTestUtils.setField(dispute, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(dispute, "transactionId", transactionId);
        ReflectionTestUtils.setField(dispute, "status", status);
        return dispute;
    }
}
