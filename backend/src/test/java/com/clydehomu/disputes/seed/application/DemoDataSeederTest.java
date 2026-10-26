package com.clydehomu.disputes.seed.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clydehomu.disputes.account.domain.Account;
import com.clydehomu.disputes.account.infrastructure.AccountRepository;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.auth.infrastructure.AppUserRepository;
import com.clydehomu.disputes.shared.config.AppProperties;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.infrastructure.BankTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class DemoDataSeederTest {

    private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final BankTransactionRepository transactions = mock(BankTransactionRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AppProperties properties = properties();
    private DemoDataSeeder seeder;

    @BeforeEach
    void setUp() {
        // Hibernate assigns ids on save; the stubs imitate that so owners can be matched to accounts.
        when(users.save(any(AppUser.class))).thenAnswer(call -> withId(call.getArgument(0)));
        when(accounts.save(any(Account.class))).thenAnswer(call -> withId(call.getArgument(0)));
        when(encoder.encode(any())).thenAnswer(call -> "hash-of-" + call.getArgument(0));
        seeder = new DemoDataSeeder(
                users, accounts, transactions, encoder, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void doesNothingWhenTheDemoCustomerAlreadyExists() {
        when(users.existsByUsername("thandi")).thenReturn(true);

        seeder.seed();

        verify(users, never()).save(any());
        verify(accounts, never()).save(any());
        verify(transactions, never()).saveAll(anyList());
    }

    @Test
    void createsTwoCustomersWithTheDemoPasswordHashed() {
        seeder.seed();

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(AppUser::getUsername, AppUser::getDisplayName, AppUser::getRole)
                .containsExactly(
                        tuple("thandi", "Thandi Mokoena", Role.CUSTOMER),
                        tuple("pieter", "Pieter van der Merwe", Role.CUSTOMER));
        assertThat(saved.getAllValues()).extracting(AppUser::getPasswordHash).containsOnly("hash-of-demo-password");
        verify(encoder, times(2)).encode("demo-password");
    }

    @Test
    void createsThreeRandAccountsOwnedByTheRightCustomers() {
        seeder.seed();

        ArgumentCaptor<AppUser> savedUsers = ArgumentCaptor.forClass(AppUser.class);
        verify(users, times(2)).save(savedUsers.capture());
        UUID thandiId = savedUsers.getAllValues().get(0).getId();
        UUID pieterId = savedUsers.getAllValues().get(1).getId();
        ArgumentCaptor<Account> savedAccounts = ArgumentCaptor.forClass(Account.class);
        verify(accounts, times(3)).save(savedAccounts.capture());

        assertThat(savedAccounts.getAllValues())
                .extracting(
                        Account::getOwnerId, Account::getProductName, Account::getMaskedNumber, Account::getCurrency)
                .containsExactly(
                        tuple(thandiId, "Everyday Cheque Account", "**** 4821", "ZAR"),
                        tuple(thandiId, "Credit Card", "**** 7710", "ZAR"),
                        tuple(pieterId, "Everyday Cheque Account", "**** 3305", "ZAR"));
        assertThat(savedAccounts.getAllValues().get(0).getAvailableBalance()).isEqualByComparingTo("18452.37");
    }

    @Test
    void createsEightyTransactionsPerAccountWithUniqueReferences() {
        seeder.seed();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BankTransaction>> batches = ArgumentCaptor.forClass(List.class);
        verify(transactions, times(3)).saveAll(batches.capture());
        assertThat(batches.getAllValues()).allSatisfy(batch -> assertThat(batch).hasSize(80));

        List<BankTransaction> all = new ArrayList<>();
        batches.getAllValues().forEach(all::addAll);
        assertThat(all).extracting(BankTransaction::getReference).doesNotHaveDuplicates();
        assertThat(all).allSatisfy(tx -> {
            assertThat(tx.getReference()).matches("TXN-\\d{8}-\\d{6}");
            assertThat(tx.getCurrency()).isEqualTo("ZAR");
            assertThat(tx.getAmount()).isGreaterThan(BigDecimal.ZERO);
            assertThat(tx.getPostedAt()).isBeforeOrEqualTo(NOW);
        });
    }

    @Test
    void producesTheSameAmountsAndMerchantsOnEveryRun() {
        List<List<String>> runs = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            BankTransactionRepository fresh = mock(BankTransactionRepository.class);
            new DemoDataSeeder(users, accounts, fresh, encoder, properties, Clock.fixed(NOW, ZoneOffset.UTC)).seed();
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<BankTransaction>> batches = ArgumentCaptor.forClass(List.class);
            verify(fresh, times(3)).saveAll(batches.capture());
            runs.add(batches.getAllValues().stream()
                    .flatMap(List::stream)
                    .map(tx -> tx.getReference() + tx.getMerchantName() + tx.getAmount() + tx.getPostedAt())
                    .toList());
        }

        assertThat(runs.get(0)).isEqualTo(runs.get(1));
    }

    @Test
    void theSeederBeanExistsByDefaultAndWhenEnabledButNotWhenDisabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(AppUserRepository.class, () -> users)
                .withBean(AccountRepository.class, () -> accounts)
                .withBean(BankTransactionRepository.class, () -> transactions)
                .withBean(PasswordEncoder.class, () -> encoder)
                .withBean(AppProperties.class, () -> properties)
                .withBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC))
                .withUserConfiguration(DemoDataSeeder.class);

        runner.run(context -> assertThat(context).hasSingleBean(DemoDataSeeder.class));
        runner.withPropertyValues("app.seed.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DemoDataSeeder.class));
        runner.withPropertyValues("app.seed.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoDataSeeder.class));
    }

    private static AppProperties properties() {
        return new AppProperties(new AppProperties.Demo("demo-password"), new AppProperties.Dispute(120));
    }

    private static <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
