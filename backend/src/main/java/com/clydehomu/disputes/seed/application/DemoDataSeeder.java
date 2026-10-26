package com.clydehomu.disputes.seed.application;

import com.clydehomu.disputes.account.domain.Account;
import com.clydehomu.disputes.account.infrastructure.AccountRepository;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.auth.infrastructure.AppUserRepository;
import com.clydehomu.disputes.dispute.infrastructure.DisputeRepository;
import com.clydehomu.disputes.seed.application.DemoCatalogue.AccountSpec;
import com.clydehomu.disputes.seed.application.DemoCatalogue.CustomerSpec;
import com.clydehomu.disputes.seed.application.DemoLedgerGenerator.Draft;
import com.clydehomu.disputes.shared.config.AppProperties;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.EligibilityPolicy;
import com.clydehomu.disputes.transaction.infrastructure.BankTransactionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the demo customers, accounts, transaction history and disputes when the application starts and
 * {@code app.seed.enabled} is true. Safe to run on every start: if the first demo customer already exists their ledgers
 * are left alone, and a customer who already has disputes gets no more.
 *
 * <p>The random generator is seeded with a constant, so every run produces the same amounts and merchants; only the
 * dates move, because they are computed back from the clock.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final long RANDOM_SEED = 20_261_026L;
    private static final Duration ACCOUNT_AGE = Duration.ofDays(400);
    private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final AppUserRepository users;
    private final AccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;
    private final DemoLedgerGenerator ledgerGenerator = new DemoLedgerGenerator();
    private final DemoDisputeSeeder disputeSeeder;

    public DemoDataSeeder(
            AppUserRepository users,
            AccountRepository accounts,
            BankTransactionRepository transactions,
            DisputeRepository disputes,
            EligibilityPolicy eligibilityPolicy,
            PasswordEncoder passwordEncoder,
            AppProperties properties,
            Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.transactions = transactions;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.disputeSeeder = new DemoDisputeSeeder(disputes, accounts, transactions, eligibilityPolicy);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        Instant now = clock.instant();
        if (users.existsByUsername(DemoCatalogue.MARKER_USERNAME)) {
            log.info("Demo customers are already present, skipping their ledgers");
        } else {
            seedLedgers(now);
        }
        // Checked per customer, so a database loaded before disputes existed gets them without being wiped.
        int disputeCount = seedDisputes(now);
        if (disputeCount > 0) {
            log.info("Seeded demo data: {} disputes", disputeCount);
        }
    }

    private void seedLedgers(Instant now) {
        Random random = new Random(RANDOM_SEED);
        // One counter for the whole run keeps every reference unique across accounts.
        AtomicInteger referenceSequence = new AtomicInteger();
        int transactionCount = 0;
        for (CustomerSpec customer : DemoCatalogue.CUSTOMERS) {
            transactionCount += seedCustomer(customer, now, random, referenceSequence);
        }
        log.info("Seeded demo data: {} customers, {} transactions", DemoCatalogue.CUSTOMERS.size(), transactionCount);
    }

    private int seedDisputes(Instant now) {
        int count = 0;
        for (CustomerSpec spec : DemoCatalogue.CUSTOMERS) {
            Optional<AppUser> customer = users.findByUsername(spec.username());
            if (customer.isPresent()) {
                count += disputeSeeder.seedFor(customer.get(), spec.disputes(), now);
            }
        }
        return count;
    }

    private int seedCustomer(CustomerSpec customer, Instant now, Random random, AtomicInteger referenceSequence) {
        Instant createdAt = now.minus(ACCOUNT_AGE);
        AppUser user = users.save(new AppUser(
                customer.username(),
                passwordEncoder.encode(properties.demo().password()),
                customer.displayName(),
                Role.CUSTOMER,
                createdAt));
        int count = 0;
        for (AccountSpec spec : customer.accounts()) {
            Account account = accounts.save(new Account(
                    user.getId(),
                    spec.maskedNumber(),
                    spec.productName(),
                    DemoCatalogue.CURRENCY,
                    spec.balance(),
                    createdAt));
            count += seedTransactions(account, spec, now, random, referenceSequence);
        }
        return count;
    }

    private int seedTransactions(
            Account account, AccountSpec spec, Instant now, Random random, AtomicInteger referenceSequence) {
        List<Draft> drafts = ledgerGenerator.generate(spec.ledger(), now, random);
        List<BankTransaction> batch = drafts.stream()
                .map(draft -> new BankTransaction(
                        account,
                        nextReference(draft, referenceSequence),
                        draft.postedAt(),
                        draft.amount(),
                        DemoCatalogue.CURRENCY,
                        draft.direction(),
                        draft.status(),
                        draft.merchantName(),
                        draft.merchantCategory(),
                        draft.channel()))
                .toList();
        transactions.saveAll(batch);
        return batch.size();
    }

    private static String nextReference(Draft draft, AtomicInteger referenceSequence) {
        String date = draft.postedAt()
                .atZone(DemoLedgerGenerator.BANK_ZONE)
                .toLocalDate()
                .format(REFERENCE_DATE);
        return "TXN-%s-%06d".formatted(date, referenceSequence.incrementAndGet());
    }
}
