package com.clydehomu.disputes.seed.application;

import com.clydehomu.disputes.dispute.domain.DisputeAction;
import com.clydehomu.disputes.dispute.domain.DisputeReason;
import com.clydehomu.disputes.seed.application.DemoLedgerGenerator.Merchant;
import com.clydehomu.disputes.seed.application.DemoLedgerGenerator.Profile;
import com.clydehomu.disputes.seed.application.DemoLedgerGenerator.Recurring;
import com.clydehomu.disputes.transaction.domain.Channel;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * The people, accounts and merchants of the demo. All names are invented and generic; the currency is always rand.
 * Adding a customer or account is a matter of adding an entry here.
 */
final class DemoCatalogue {

    static final String CURRENCY = "ZAR";
    /** The seeder treats the presence of this user as "demo data already loaded". */
    static final String MARKER_USERNAME = "thandi";

    /** The back-office agent who works the seeded disputes; created on its own so older databases gain it too. */
    static final String AGENT_USERNAME = "lerato";

    static final String AGENT_DISPLAY_NAME = "Lerato Dlamini";

    record AccountSpec(String productName, String maskedNumber, BigDecimal balance, Profile ledger) {}

    /** One move in a demo dispute's history, made {@code after} the previous event by whoever the action belongs to. */
    record DisputeStep(DisputeAction action, Duration after, String message, String internalNote) {}

    /**
     * A dispute to seed. The transaction is not named: the seeder picks the latest eligible debit on the account that
     * fits (optionally by merchant), so the data stays valid whatever day it is loaded. The description is a template
     * whose only placeholder is the merchant name.
     */
    record DisputeSpec(
            String accountProduct,
            Duration raisedAgo,
            DisputeReason reason,
            int percentOfAmount,
            String merchantName,
            String description,
            List<DisputeStep> steps) {}

    record CustomerSpec(String username, String displayName, List<AccountSpec> accounts, List<DisputeSpec> disputes) {}

    private static final String CHEQUE = "Everyday Cheque Account";
    private static final String CARD = "Credit Card";

    private static final Merchant FRESH_MARKET = card("Fresh Market Midrand", "Groceries", 8_500, 95_000);
    private static final Merchant QUICK_MART = card("Quick Mart Centurion", "Groceries", 4_000, 42_000);
    private static final Merchant METRO_FUEL = card("Metro Fuel N1", "Fuel", 45_000, 135_000);
    private static final Merchant SAVANNA_FUEL = card("Savanna Fuel Stop", "Fuel", 40_000, 110_000);
    private static final Merchant BAKERY = card("Corner Bakery Rosebank", "Restaurants", 4_500, 21_000);
    private static final Merchant BURGERS = card("Karoo Burgers Sandton", "Restaurants", 9_000, 48_000);
    private static final Merchant COFFEE = card("Lekker Coffee Co", "Coffee", 2_800, 9_500);
    private static final Merchant PHARMACY = card("Bayside Pharmacy", "Health", 6_000, 78_000);
    private static final Merchant CLOTHING = card("Urban Threads Menlyn", "Clothing", 25_000, 180_000);
    private static final Merchant ELECTRONICS = card("TechHub Online", "Electronics", 19_900, 420_000);
    private static final Merchant PARKING = card("City Parking Rosebank", "Parking", 2_000, 9_000);
    private static final Merchant RIDES = card("Ride Hail Gauteng", "Transport", 4_500, 32_000);
    private static final Merchant HARDWARE = card("Bright Home Hardware", "Home", 8_000, 150_000);
    private static final Merchant TOLLS = card("Gauteng Toll Services", "Tolls", 3_000, 16_000);
    private static final Merchant STREAMBOX = card("StreamBox Subscription", "Digital subscriptions", 18_900, 18_900);
    private static final Merchant ATM_SANDTON =
            new Merchant("ATM Cash Withdrawal Sandton", "Cash", Channel.ATM, 20_000, 200_000, 10_000);
    private static final Merchant ATM_MENLYN =
            new Merchant("ATM Cash Withdrawal Menlyn", "Cash", Channel.ATM, 20_000, 150_000, 10_000);
    private static final Merchant PLUMBER =
            new Merchant("EFT Payment - Bluegum Plumbing", "Home services", Channel.EFT, 35_000, 280_000, 5_000);

    private static final List<Merchant> CARD_MERCHANTS = List.of(
            FRESH_MARKET,
            QUICK_MART,
            METRO_FUEL,
            SAVANNA_FUEL,
            BAKERY,
            BURGERS,
            COFFEE,
            PHARMACY,
            CLOTHING,
            ELECTRONICS,
            PARKING,
            RIDES,
            HARDWARE,
            TOLLS);
    private static final List<Merchant> CHEQUE_MERCHANTS = Stream.concat(
                    CARD_MERCHANTS.stream(), Stream.of(ATM_SANDTON, ATM_MENLYN, PLUMBER))
            .toList();

    private static final int TRANSACTIONS_PER_ACCOUNT = 80;

    static final List<CustomerSpec> CUSTOMERS = List.of(
            new CustomerSpec(
                    MARKER_USERNAME,
                    "Thandi Mokoena",
                    List.of(
                            new AccountSpec(
                                    CHEQUE,
                                    "**** 4821",
                                    new BigDecimal("18452.37"),
                                    new Profile(
                                            TRANSACTIONS_PER_ACCOUNT,
                                            new Recurring(
                                                    "Northwind Payroll",
                                                    "Salary",
                                                    Channel.EFT,
                                                    new BigDecimal("28500.00"),
                                                    25),
                                            new Recurring(
                                                    "Sunrise Gym Membership",
                                                    "Health and fitness",
                                                    Channel.DEBIT_ORDER,
                                                    new BigDecimal("449.00"),
                                                    1),
                                            STREAMBOX,
                                            new BigDecimal("189.00"),
                                            CHEQUE_MERCHANTS)),
                            new AccountSpec(
                                    CARD,
                                    "**** 7710",
                                    new BigDecimal("24100.00"),
                                    new Profile(
                                            TRANSACTIONS_PER_ACCOUNT,
                                            new Recurring(
                                                    "Payment Received - Thank You",
                                                    "Card payment",
                                                    Channel.EFT,
                                                    new BigDecimal("5500.00"),
                                                    28),
                                            null,
                                            FRESH_MARKET,
                                            new BigDecimal("642.15"),
                                            CARD_MERCHANTS))),
                    // One dispute per status, oldest first. Each is raised well before "now" and every later step is
                    // after the one before it, so the last event is always in the past. The newest few debits on each
                    // account stay undisputed on purpose (see DemoDisputeSeeder), including the StreamBox duplicate.
                    List.of(
                            new DisputeSpec(
                                    CARD,
                                    Duration.ofDays(27).plusHours(3),
                                    DisputeReason.F01,
                                    100,
                                    null,
                                    "I do not recognise this payment to %s. My card was with me the whole day and I have not shared my PIN with anyone.",
                                    List.of(
                                            step(
                                                    DisputeAction.CLAIM,
                                                    Duration.ofHours(4),
                                                    "A dispute analyst has picked up your case and started the investigation."),
                                            step(
                                                    DisputeAction.REQUEST_INFO,
                                                    Duration.ofDays(1),
                                                    "Please confirm that your card was with you on the day and that you have not shared your PIN or card details."),
                                            step(
                                                    DisputeAction.CUSTOMER_RESPOND,
                                                    Duration.ofDays(2),
                                                    "My card was in my wallet all day and I have never shared my PIN with anyone."),
                                            resolution(
                                                    DisputeAction.RESOLVE_UPHELD,
                                                    Duration.ofDays(3),
                                                    "The merchant could not show that you authorised this payment, so we have upheld your dispute.",
                                                    "No signed slip or chip and PIN record from the acquirer. Customer statement consistent. Chargeback raised under fraud code F01."))),
                            new DisputeSpec(
                                    CARD,
                                    Duration.ofDays(19).plusHours(2),
                                    DisputeReason.C03,
                                    100,
                                    null,
                                    "The merchant told me my refund for %s was approved weeks ago, but I have not seen the money in my account.",
                                    List.of(
                                            step(
                                                    DisputeAction.CLAIM,
                                                    Duration.ofDays(1),
                                                    "We've asked the merchant to confirm when and how the refund was paid."),
                                            resolution(
                                                    DisputeAction.RESOLVE_DECLINED,
                                                    Duration.ofDays(6),
                                                    "The merchant showed that the refund was paid back to your card on the day it was approved, so we couldn't uphold this dispute.",
                                                    "Merchant supplied the refund reference and settlement date. Credit is visible on the card ledger. Closed as declined."))),
                            new DisputeSpec(
                                    CHEQUE,
                                    Duration.ofDays(14),
                                    DisputeReason.P01,
                                    100,
                                    null,
                                    "I can see two charges from %s for what I believe was a single purchase and I would like the second one reversed.",
                                    List.of(
                                            step(
                                                    DisputeAction.CLAIM,
                                                    Duration.ofHours(3),
                                                    "We're comparing the two charges with the merchant's records."),
                                            step(DisputeAction.WITHDRAW, Duration.ofDays(2), null))),
                            new DisputeSpec(
                                    CHEQUE,
                                    Duration.ofDays(9),
                                    DisputeReason.P02,
                                    40,
                                    null,
                                    "The amount charged by %s is more than the price I agreed to pay. I would like the difference refunded.",
                                    List.of(
                                            step(
                                                    DisputeAction.CLAIM,
                                                    Duration.ofHours(5),
                                                    "A dispute analyst is reviewing the amount charged."),
                                            step(
                                                    DisputeAction.REQUEST_INFO,
                                                    Duration.ofDays(1),
                                                    "Please send us the till slip or invoice showing the amount you agreed to pay."))),
                            new DisputeSpec(
                                    CHEQUE,
                                    Duration.ofDays(5),
                                    DisputeReason.C01,
                                    100,
                                    null,
                                    "I paid %s for goods that were never delivered. I have contacted them twice without a response.",
                                    List.of(step(
                                            DisputeAction.CLAIM,
                                            Duration.ofHours(3),
                                            "We've asked the merchant for proof of delivery."))),
                            new DisputeSpec(
                                    CHEQUE,
                                    Duration.ofDays(2).plusHours(4),
                                    DisputeReason.C02,
                                    100,
                                    "Sunrise Gym Membership",
                                    "I cancelled my %s in writing last month but the debit order has continued to be taken from my account.",
                                    List.of()))),
            new CustomerSpec(
                    "pieter",
                    "Pieter van der Merwe",
                    List.of(new AccountSpec(
                            CHEQUE,
                            "**** 3305",
                            new BigDecimal("9235.80"),
                            new Profile(
                                    TRANSACTIONS_PER_ACCOUNT,
                                    new Recurring(
                                            "Harbour Logistics Payroll",
                                            "Salary",
                                            Channel.EFT,
                                            new BigDecimal("41200.00"),
                                            27),
                                    new Recurring(
                                            "Cape Fibre Internet",
                                            "Utilities",
                                            Channel.DEBIT_ORDER,
                                            new BigDecimal("899.00"),
                                            5),
                                    PHARMACY,
                                    new BigDecimal("312.40"),
                                    CHEQUE_MERCHANTS))),
                    // Two unclaimed disputes shape the default queue for the SLA demo (ADR 0007). The fraud case is
                    // past
                    // its 10 days, so it is overdue and, ordered by due date, sits above the older consumer case that
                    // still has 17 days left. Their ages are relative to "now", so this stays true whichever day the
                    // data is loaded.
                    List.of(
                            new DisputeSpec(
                                    CHEQUE,
                                    Duration.ofDays(13).plusHours(2),
                                    DisputeReason.C03,
                                    100,
                                    null,
                                    "I returned an item to %s and was promised a refund, but the money has not reached my account.",
                                    List.of()),
                            new DisputeSpec(
                                    CHEQUE,
                                    Duration.ofDays(12).plusHours(5),
                                    DisputeReason.F01,
                                    100,
                                    null,
                                    "I do not recognise this payment to %s. I have not shared my card or my PIN with anyone.",
                                    List.of()),
                            new DisputeSpec(
                                    CHEQUE,
                                    Duration.ofDays(6).plusHours(1),
                                    DisputeReason.F02,
                                    100,
                                    null,
                                    "My card was stolen at a restaurant and I did not make this payment to %s. I have already blocked the card.",
                                    List.of(
                                            step(
                                                    DisputeAction.CLAIM,
                                                    Duration.ofHours(2),
                                                    "Your card has been blocked and a fraud analyst is reviewing this payment."))))));

    private DemoCatalogue() {}

    private static DisputeStep step(DisputeAction action, Duration after, String message) {
        return new DisputeStep(action, after, message, null);
    }

    /** Resolving is the one step that also records a back-office note. */
    private static DisputeStep resolution(DisputeAction action, Duration after, String message, String internalNote) {
        return new DisputeStep(action, after, message, internalNote);
    }

    private static Merchant card(String name, String category, int minCents, int maxCents) {
        return new Merchant(name, category, Channel.CARD, minCents, maxCents, 1);
    }
}
