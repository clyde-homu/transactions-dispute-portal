package com.clydehomu.disputes.seed.application;

import com.clydehomu.disputes.transaction.domain.Channel;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Builds a believable transaction history for one account. Pure and deterministic: the same profile, clock reading and
 * random seed always give the same list, and every date is computed back from "now" so the data never goes stale.
 *
 * <p>Besides ordinary spending, each history contains the cases the demo needs: a monthly credit, an optional monthly
 * debit order, one obvious duplicate charge, two pending purchases and two refunds. Roughly a fifth of the
 * transactions are older than the default 120-day dispute window.
 */
final class DemoLedgerGenerator {

    static final int HISTORY_DAYS = 150;
    static final ZoneId BANK_ZONE = ZoneId.of("Africa/Johannesburg");

    private static final List<Duration> PENDING_AGES = List.of(Duration.ofHours(3), Duration.ofHours(19));
    private static final int REFUND_COUNT = 2;
    private static final int DUPLICATE_DAYS_AGO = 5;
    private static final LocalTime CREDIT_TIME = LocalTime.of(7, 30);
    private static final LocalTime DEBIT_ORDER_TIME = LocalTime.of(0, 5);
    private static final LocalTime DUPLICATE_FIRST_TIME = LocalTime.of(9, 14);
    private static final LocalTime DUPLICATE_SECOND_TIME = LocalTime.of(9, 16);

    enum Kind {
        MONTHLY_CREDIT,
        MONTHLY_DEBIT_ORDER,
        DUPLICATE_CHARGE,
        PENDING,
        REFUND,
        SPEND
    }

    /** One transaction before it is given a reference and attached to a stored account. */
    record Draft(
            Kind kind,
            Instant postedAt,
            BigDecimal amount,
            TransactionDirection direction,
            TransactionStatus status,
            String merchantName,
            String merchantCategory,
            Channel channel) {}

    /** A place the customer spends at; amounts are drawn in whole steps between the two bounds. */
    record Merchant(String name, String category, Channel channel, int minCents, int maxCents, int stepCents) {}

    /** Something that repeats on the same day every month. */
    record Recurring(String name, String category, Channel channel, BigDecimal amount, int dayOfMonth) {}

    record Profile(
            int size,
            Recurring monthlyCredit,
            Recurring monthlyDebitOrder,
            Merchant duplicatedMerchant,
            BigDecimal duplicatedAmount,
            List<Merchant> merchants) {}

    List<Draft> generate(Profile profile, Instant now, Random random) {
        LocalDate today = now.atZone(BANK_ZONE).toLocalDate();
        LocalDate firstDay = today.minusDays(HISTORY_DAYS - 1L);
        List<Draft> drafts = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        addRecurring(
                drafts,
                profile.monthlyCredit(),
                Kind.MONTHLY_CREDIT,
                TransactionDirection.CREDIT,
                CREDIT_TIME,
                firstDay,
                today,
                now);
        if (profile.monthlyDebitOrder() != null) {
            addRecurring(
                    drafts,
                    profile.monthlyDebitOrder(),
                    Kind.MONTHLY_DEBIT_ORDER,
                    TransactionDirection.DEBIT,
                    DEBIT_ORDER_TIME,
                    firstDay,
                    today,
                    now);
        }
        addDuplicateCharge(drafts, seen, profile, today);
        addPending(drafts, profile, now, random);
        addRefunds(drafts, profile, now, random);
        if (drafts.size() > profile.size()) {
            throw new IllegalArgumentException("Profile size " + profile.size() + " is smaller than its fixed entries");
        }
        while (drafts.size() < profile.size()) {
            addSpend(drafts, seen, profile, now, random);
        }

        drafts.sort(Comparator.comparing(Draft::postedAt));
        return drafts;
    }

    private static void addRecurring(
            List<Draft> drafts,
            Recurring recurring,
            Kind kind,
            TransactionDirection direction,
            LocalTime time,
            LocalDate firstDay,
            LocalDate today,
            Instant now) {
        for (LocalDate day = firstDay; !day.isAfter(today); day = day.plusDays(1)) {
            // A "31st" item falls on the last day of shorter months instead of being skipped.
            int dueDay = Math.min(recurring.dayOfMonth(), day.lengthOfMonth());
            Instant postedAt = day.atTime(time).atZone(BANK_ZONE).toInstant();
            if (day.getDayOfMonth() == dueDay && !postedAt.isAfter(now)) {
                drafts.add(new Draft(
                        kind,
                        postedAt,
                        recurring.amount(),
                        direction,
                        TransactionStatus.SETTLED,
                        recurring.name(),
                        recurring.category(),
                        recurring.channel()));
            }
        }
    }

    private static void addDuplicateCharge(List<Draft> drafts, Set<String> seen, Profile profile, LocalDate today) {
        LocalDate day = today.minusDays(DUPLICATE_DAYS_AGO);
        Merchant merchant = profile.duplicatedMerchant();
        for (LocalTime time : List.of(DUPLICATE_FIRST_TIME, DUPLICATE_SECOND_TIME)) {
            drafts.add(new Draft(
                    Kind.DUPLICATE_CHARGE,
                    day.atTime(time).atZone(BANK_ZONE).toInstant(),
                    profile.duplicatedAmount(),
                    TransactionDirection.DEBIT,
                    TransactionStatus.SETTLED,
                    merchant.name(),
                    merchant.category(),
                    merchant.channel()));
        }
        seen.add(key(merchant.name(), profile.duplicatedAmount(), day));
    }

    private static void addPending(List<Draft> drafts, Profile profile, Instant now, Random random) {
        List<Merchant> cardMerchants = profile.merchants().stream()
                .filter(m -> m.channel() == Channel.CARD)
                .toList();
        // Pending items are always the newest on the account, as they would be at a real bank.
        for (Duration age : PENDING_AGES) {
            Merchant merchant = cardMerchants.get(random.nextInt(cardMerchants.size()));
            drafts.add(new Draft(
                    Kind.PENDING,
                    now.minus(age),
                    amountFor(merchant, random),
                    TransactionDirection.DEBIT,
                    TransactionStatus.PENDING,
                    merchant.name(),
                    merchant.category(),
                    Channel.CARD));
        }
    }

    private static void addRefunds(List<Draft> drafts, Profile profile, Instant now, Random random) {
        List<Merchant> cardMerchants = profile.merchants().stream()
                .filter(m -> m.channel() == Channel.CARD)
                .toList();
        for (int i = 0; i < REFUND_COUNT; i++) {
            Merchant merchant = cardMerchants.get(random.nextInt(cardMerchants.size()));
            int daysAgo = 20 + random.nextInt(80);
            drafts.add(new Draft(
                    Kind.REFUND,
                    now.minus(Duration.ofDays(daysAgo)).minus(Duration.ofMinutes(random.nextInt(24 * 60))),
                    BigDecimal.valueOf(5_000 + random.nextInt(55_000), 2),
                    TransactionDirection.CREDIT,
                    TransactionStatus.SETTLED,
                    "Refund: " + merchant.name(),
                    merchant.category(),
                    Channel.CARD));
        }
    }

    private static void addSpend(List<Draft> drafts, Set<String> seen, Profile profile, Instant now, Random random) {
        Merchant merchant =
                profile.merchants().get(random.nextInt(profile.merchants().size()));
        // At least a day old, so everything newer than that is the pending pair.
        int daysAgo = 1 + random.nextInt(HISTORY_DAYS - 1);
        Instant postedAt = now.minus(Duration.ofDays(daysAgo)).minus(Duration.ofMinutes(random.nextInt(24 * 60)));
        BigDecimal amount = amountFor(merchant, random);
        // A repeat of merchant, amount and day would be a second "duplicate"; the demo has exactly one.
        if (!seen.add(key(merchant.name(), amount, postedAt.atZone(BANK_ZONE).toLocalDate()))) {
            return;
        }
        drafts.add(new Draft(
                Kind.SPEND,
                postedAt,
                amount,
                TransactionDirection.DEBIT,
                TransactionStatus.SETTLED,
                merchant.name(),
                merchant.category(),
                merchant.channel()));
    }

    private static BigDecimal amountFor(Merchant merchant, Random random) {
        int steps = (merchant.maxCents() - merchant.minCents()) / merchant.stepCents();
        return BigDecimal.valueOf(merchant.minCents() + (long) random.nextInt(steps + 1) * merchant.stepCents(), 2);
    }

    private static String key(String merchantName, BigDecimal amount, LocalDate day) {
        return merchantName + '|' + amount.toPlainString() + '|' + day;
    }
}
