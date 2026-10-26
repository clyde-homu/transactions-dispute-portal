package com.clydehomu.disputes.seed.application;

import com.clydehomu.disputes.seed.application.DemoLedgerGenerator.Merchant;
import com.clydehomu.disputes.seed.application.DemoLedgerGenerator.Profile;
import com.clydehomu.disputes.seed.application.DemoLedgerGenerator.Recurring;
import com.clydehomu.disputes.transaction.domain.Channel;
import java.math.BigDecimal;
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

    record AccountSpec(String productName, String maskedNumber, BigDecimal balance, Profile ledger) {}

    record CustomerSpec(String username, String displayName, List<AccountSpec> accounts) {}

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
                                    "Everyday Cheque Account",
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
                                    "Credit Card",
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
                                            CARD_MERCHANTS)))),
            new CustomerSpec(
                    "pieter",
                    "Pieter van der Merwe",
                    List.of(new AccountSpec(
                            "Everyday Cheque Account",
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
                                    CHEQUE_MERCHANTS)))));

    private DemoCatalogue() {}

    private static Merchant card(String name, String category, int minCents, int maxCents) {
        return new Merchant(name, category, Channel.CARD, minCents, maxCents, 1);
    }
}
