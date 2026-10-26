package com.clydehomu.disputes.transaction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransactionSpecificationsTest {

    @Test
    void likeWildcardsInTheSearchTextAreTakenLiterally() {
        assertThat(TransactionSpecifications.escapeLike("50%_off")).isEqualTo("50\\%\\_off");
    }

    @Test
    void theEscapeCharacterItselfIsEscapedFirst() {
        assertThat(TransactionSpecifications.escapeLike("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void ordinaryTextIsUntouched() {
        assertThat(TransactionSpecifications.escapeLike("streambox")).isEqualTo("streambox");
    }
}
