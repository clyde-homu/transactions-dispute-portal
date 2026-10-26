package com.clydehomu.disputes.seed.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clydehomu.disputes.seed.application.DemoLedgerGenerator.Draft;
import com.clydehomu.disputes.seed.application.DemoLedgerGenerator.Kind;
import com.clydehomu.disputes.seed.application.DemoLedgerGenerator.Merchant;
import com.clydehomu.disputes.seed.application.DemoLedgerGenerator.Profile;
import com.clydehomu.disputes.seed.application.DemoLedgerGenerator.Recurring;
import com.clydehomu.disputes.transaction.domain.Channel;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DemoLedgerGeneratorTest {

    private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");
    private static final long SEED = 42L;

    private final DemoLedgerGenerator generator = new DemoLedgerGenerator();

    static Stream<Arguments> demoAccounts() {
        return DemoCatalogue.CUSTOMERS.stream()
                .flatMap(customer -> customer.accounts().stream()
                        .map(account ->
                                Arguments.of(customer.username() + " / " + account.productName(), account.ledger())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoAccounts")
    void producesTheRequestedNumberOfTransactionsOldestFirst(String name, Profile profile) {
        List<Draft> ledger = generate(profile);

        assertThat(ledger).hasSize(80);
        assertThat(ledger).extracting(Draft::postedAt).isSorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoAccounts")
    void spansOneHundredFiftyDaysAndNeverReachesIntoTheFuture(String name, Profile profile) {
        List<Draft> ledger = generate(profile);

        assertThat(ledger).allSatisfy(draft -> {
            assertThat(draft.postedAt()).isAfterOrEqualTo(NOW.minus(Duration.ofDays(150)));
            assertThat(draft.postedAt()).isBeforeOrEqualTo(NOW);
        });
        assertThat(ledger.getFirst().postedAt()).isBefore(NOW.minus(Duration.ofDays(120)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoAccounts")
    void hasSomeTransactionsTooOldToDisputeAndMostStillInsideTheWindow(String name, Profile profile) {
        Instant windowStart = NOW.minus(Duration.ofDays(120));
        List<Draft> ledger = generate(profile);

        long tooOld =
                ledger.stream().filter(d -> d.postedAt().isBefore(windowStart)).count();

        assertThat(tooOld).isBetween(5L, 30L);
        assertThat(ledger.size() - tooOld).isGreaterThan(40);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoAccounts")
    void hasExactlyTwoPendingPurchasesAndTheyAreTheNewest(String name, Profile profile) {
        List<Draft> ledger = generate(profile);

        List<Draft> pending = ledger.stream()
                .filter(d -> d.status() == TransactionStatus.PENDING)
                .toList();

        assertThat(pending).hasSize(2).allSatisfy(draft -> {
            assertThat(draft.kind()).isEqualTo(Kind.PENDING);
            assertThat(draft.direction()).isEqualTo(TransactionDirection.DEBIT);
            assertThat(draft.postedAt()).isAfter(NOW.minus(Duration.ofDays(1)));
        });
        // Everything settled that is not a scheduled item is at least a day old.
        assertThat(ledger.stream().filter(d -> d.kind() == Kind.SPEND))
                .allSatisfy(d -> assertThat(d.postedAt()).isBefore(NOW.minus(Duration.ofHours(23))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoAccounts")
    void hasExactlyOneObviousDuplicateCharge(String name, Profile profile) {
        List<Draft> ledger = generate(profile);

        List<Draft> duplicates =
                ledger.stream().filter(d -> d.kind() == Kind.DUPLICATE_CHARGE).toList();
        assertThat(duplicates).hasSize(2);
        assertThat(duplicates)
                .extracting(Draft::merchantName)
                .containsOnly(profile.duplicatedMerchant().name());
        assertThat(duplicates).extracting(Draft::amount).containsOnly(profile.duplicatedAmount());
        assertThat(duplicates.stream().map(DemoLedgerGeneratorTest::day).distinct())
                .hasSize(1);

        // No other merchant/amount/day combination repeats anywhere on the account.
        Map<String, Long> repeats = ledger.stream()
                .collect(Collectors.groupingBy(
                        d -> d.merchantName() + '|' + d.amount() + '|' + day(d), Collectors.counting()));
        assertThat(repeats.values().stream().filter(count -> count > 1)).hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoAccounts")
    void hasAMonthlyCreditAndTwoRefunds(String name, Profile profile) {
        List<Draft> ledger = generate(profile);

        List<Draft> monthly =
                ledger.stream().filter(d -> d.kind() == Kind.MONTHLY_CREDIT).toList();
        assertThat(monthly.size()).isBetween(4, 6);
        assertThat(monthly).allSatisfy(draft -> {
            assertThat(draft.direction()).isEqualTo(TransactionDirection.CREDIT);
            assertThat(draft.amount()).isEqualTo(profile.monthlyCredit().amount());
            assertThat(draft.merchantName()).isEqualTo(profile.monthlyCredit().name());
        });

        List<Draft> refunds =
                ledger.stream().filter(d -> d.kind() == Kind.REFUND).toList();
        assertThat(refunds).hasSize(2).allSatisfy(draft -> {
            assertThat(draft.direction()).isEqualTo(TransactionDirection.CREDIT);
            assertThat(draft.merchantName()).startsWith("Refund: ");
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoAccounts")
    void hasTheMonthlyDebitOrderWhenTheProfileHasOne(String name, Profile profile) {
        List<Draft> orders = generate(profile).stream()
                .filter(d -> d.kind() == Kind.MONTHLY_DEBIT_ORDER)
                .toList();

        if (profile.monthlyDebitOrder() == null) {
            assertThat(orders).isEmpty();
            return;
        }
        assertThat(orders.size()).isBetween(4, 6);
        assertThat(orders).allSatisfy(draft -> {
            assertThat(draft.channel()).isEqualTo(Channel.DEBIT_ORDER);
            assertThat(draft.direction()).isEqualTo(TransactionDirection.DEBIT);
            assertThat(draft.amount()).isEqualTo(profile.monthlyDebitOrder().amount());
        });
        assertThat(orders.stream()
                        .map(d -> d.postedAt()
                                .atZone(DemoLedgerGenerator.BANK_ZONE)
                                .getDayOfMonth())
                        .distinct())
                .hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoAccounts")
    void everyAmountIsPositiveWithTwoDecimals(String name, Profile profile) {
        assertThat(generate(profile)).allSatisfy(draft -> {
            assertThat(draft.amount()).isPositive();
            assertThat(draft.amount().scale()).isEqualTo(2);
        });
    }

    @Test
    void chequeAccountsMixAllFourChannelsAndTheCardAccountDoesNot() {
        Profile cheque = DemoCatalogue.CUSTOMERS.getFirst().accounts().get(0).ledger();
        Profile card = DemoCatalogue.CUSTOMERS.getFirst().accounts().get(1).ledger();

        assertThat(generate(cheque)).extracting(Draft::channel).contains(Channel.values());
        assertThat(generate(card)).extracting(Draft::channel).doesNotContain(Channel.ATM, Channel.DEBIT_ORDER);
    }

    @Test
    void sameInputsGiveTheSameLedger() {
        Profile profile =
                DemoCatalogue.CUSTOMERS.getFirst().accounts().getFirst().ledger();

        assertThat(generator.generate(profile, NOW, new Random(SEED)))
                .isEqualTo(generator.generate(profile, NOW, new Random(SEED)));
    }

    @Test
    void aDifferentRandomSeedGivesDifferentSpending() {
        Profile profile =
                DemoCatalogue.CUSTOMERS.getFirst().accounts().getFirst().ledger();

        assertThat(generator.generate(profile, NOW, new Random(1)))
                .isNotEqualTo(generator.generate(profile, NOW, new Random(2)));
    }

    @Test
    void datesFollowTheClockSoTheDataNeverGoesStale() {
        Profile profile =
                DemoCatalogue.CUSTOMERS.getFirst().accounts().getFirst().ledger();
        Instant later = NOW.plus(Duration.ofDays(200));

        List<Draft> ledger = generator.generate(profile, later, new Random(SEED));

        assertThat(ledger).allSatisfy(draft -> {
            assertThat(draft.postedAt()).isBeforeOrEqualTo(later);
            assertThat(draft.postedAt()).isAfterOrEqualTo(later.minus(Duration.ofDays(150)));
        });
    }

    @Test
    void aMonthlyItemDueOnTheThirtyFirstFallsOnTheLastDayOfShorterMonths() {
        Merchant shop = new Merchant("Corner Shop", "Groceries", Channel.CARD, 1_000, 5_000, 1);
        Profile profile = new Profile(
                30,
                new Recurring("Payroll", "Salary", Channel.EFT, new BigDecimal("1000.00"), 31),
                null,
                shop,
                new BigDecimal("55.00"),
                List.of(shop));
        Instant march = Instant.parse("2026-03-05T10:00:00Z");

        List<LocalDate> creditDays = generator.generate(profile, march, new Random(SEED)).stream()
                .filter(d -> d.kind() == Kind.MONTHLY_CREDIT)
                .map(DemoLedgerGeneratorTest::day)
                .toList();

        assertThat(creditDays)
                .contains(LocalDate.of(2025, 11, 30), LocalDate.of(2025, 12, 31), LocalDate.of(2026, 2, 28));
    }

    @Test
    void aProfileSmallerThanItsFixedEntriesIsRejected() {
        Merchant shop = new Merchant("Corner Shop", "Groceries", Channel.CARD, 1_000, 5_000, 1);
        Profile tooSmall = new Profile(
                3,
                new Recurring("Payroll", "Salary", Channel.EFT, new BigDecimal("1000.00"), 25),
                null,
                shop,
                new BigDecimal("55.00"),
                List.of(shop));

        assertThatThrownBy(() -> generator.generate(tooSmall, NOW, new Random(SEED)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theCatalogueHasTwoCustomersWithThreeUniquelyNumberedAccounts() {
        assertThat(DemoCatalogue.CUSTOMERS).extracting(c -> c.username()).containsExactly("thandi", "pieter");
        assertThat(DemoCatalogue.CUSTOMERS.getFirst().username()).isEqualTo(DemoCatalogue.MARKER_USERNAME);
        assertThat(DemoCatalogue.CUSTOMERS.stream()
                        .mapToInt(c -> c.accounts().size())
                        .sum())
                .isEqualTo(3);
        assertThat(DemoCatalogue.CUSTOMERS.stream()
                        .flatMap(c -> c.accounts().stream())
                        .collect(Collectors.toMap(a -> a.maskedNumber(), Function.identity())))
                .as("masked numbers are unique")
                .hasSize(3);
    }

    private List<Draft> generate(Profile profile) {
        return generator.generate(profile, NOW, new Random(SEED));
    }

    private static LocalDate day(Draft draft) {
        return draft.postedAt().atZone(DemoLedgerGenerator.BANK_ZONE).toLocalDate();
    }
}
