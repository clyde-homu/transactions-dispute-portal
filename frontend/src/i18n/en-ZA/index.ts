// Every user-facing string lives here. Keys are grouped by screen so the next feature
// phase can add its own block without touching the shared ones.
export default {
  app: {
    name: 'Dispute Portal',
    skipToContent: 'Skip to main content',
  },
  nav: {
    primary: 'Main navigation',
    transactions: 'Transactions',
    disputes: 'Disputes',
    signOut: 'Sign out',
    signedInAs: 'Signed in as {name}',
  },
  auth: {
    sessionExpired: 'Your session has ended. Please sign in again.',
    signOutFailed:
      'We could not reach the server to end your session. You have been signed out on this device.',
  },
  login: {
    title: 'Sign in',
    subtitle: 'View your transactions and follow your disputes.',
    username: 'Username',
    password: 'Password',
    showPassword: 'Show password',
    hidePassword: 'Hide password',
    submit: 'Sign in',
    demoHint: 'Demo users: thandi or pieter, password demo-password',
    validation: {
      usernameRequired: 'Enter your username.',
      usernameTooLong: 'Your username can be at most 50 characters.',
      passwordRequired: 'Enter your password.',
      passwordTooLong: 'Your password can be at most 72 characters.',
    },
  },
  // Keyed by the stable problem code from the API; the server text is only a fallback.
  errors: {
    VALIDATION_FAILED: 'Some of the details need your attention.',
    BAD_CREDENTIALS: 'The username or password is incorrect.',
    UNAUTHENTICATED: 'Your session has ended. Please sign in again.',
    FORBIDDEN: 'You do not have permission to do that.',
    NOT_FOUND: 'We could not find what you were looking for.',
    TRANSACTION_NOT_ELIGIBLE: 'This transaction cannot be disputed.',
    DISPUTE_ALREADY_EXISTS: 'A dispute already exists for this transaction.',
    INVALID_DISPUTE_TRANSITION: 'This dispute can no longer be changed that way.',
    TOO_MANY_LOGIN_ATTEMPTS: 'Too many failed sign-in attempts. Try again in a few minutes.',
    METHOD_NOT_ALLOWED: 'That action is not available here.',
    NOT_ACCEPTABLE: 'The server could not provide the information in the format requested.',
    UNSUPPORTED_MEDIA_TYPE: 'The server could not read what was sent. Please try again.',
    CLIENT_ERROR: 'That request could not be completed. Please check the details and try again.',
    INTERNAL_ERROR: 'Something went wrong on our side. Please try again.',
    NETWORK_ERROR: 'We could not reach the server. Check your connection and try again.',
    UNKNOWN_ERROR: 'Something unexpected happened. Please try again.',
  },
  common: {
    retry: 'Try again',
  },
  money: {
    debit: 'Debit',
    credit: 'Credit',
  },
  // Customer-facing wording for each dispute status (from the contract).
  disputeStatus: {
    SUBMITTED: 'Submitted',
    UNDER_REVIEW: 'In review',
    AWAITING_CUSTOMER_INFO: 'Action needed',
    RESOLVED_UPHELD: 'Resolved in your favour',
    RESOLVED_DECLINED: 'Declined',
    WITHDRAWN: 'Withdrawn',
  },
  transactionStatus: {
    PENDING: 'Pending',
    SETTLED: 'Settled',
  },
  channel: {
    CARD: 'Card',
    DEBIT_ORDER: 'Debit order',
    EFT: 'EFT',
    ATM: 'ATM',
  },
  transactions: {
    accounts: {
      label: 'Your accounts',
      availableBalance: 'Available balance',
      loading: 'Loading accounts',
      emptyTitle: 'No accounts to show',
      emptyDescription:
        'We could not find any accounts for you. If this looks wrong, please contact the bank.',
      errorTitle: 'We could not load your accounts',
    },
    filters: {
      label: 'Filter transactions',
      rangeLabel: 'Date range',
      range: {
        '30': 'Last 30 days',
        '90': 'Last 90 days',
        '120': 'Last 120 days',
        all: 'All',
      },
      search: 'Search merchant or reference',
      disputedOnly: 'Disputed only',
      clear: 'Clear filters',
    },
    list: {
      loading: 'Loading transactions',
      summary: 'Showing {from}–{to} of {total} transactions',
      pagination: 'Transaction pages',
      column: {
        date: 'Date',
        merchant: 'Merchant',
        reference: 'Reference',
        amount: 'Amount',
      },
      errorTitle: 'We could not load your transactions',
      emptyFilteredTitle: 'No transactions match these filters',
      emptyFilteredDescription:
        'Try a longer date range, a different search, or clear the filters.',
      emptyTitle: 'No transactions on this account yet',
      emptyDescription: 'New card payments, debit orders and transfers will appear here.',
    },
    badge: {
      disputed: 'Disputed',
    },
  },
  transactionDetail: {
    back: 'Back to transactions',
    loading: 'Loading transaction',
    errorTitle: 'We could not load this transaction',
    notFoundTitle: 'We could not find that transaction',
    notFoundDescription:
      'It may belong to someone else, or the link may be out of date. Go back to your transactions to find it.',
    summary: 'Transaction summary',
    fields: {
      postedAt: 'Date and time',
      reference: 'Reference',
      channel: 'Channel',
      category: 'Category',
      status: 'Status',
      type: 'Type',
    },
    eligibility: {
      heading: 'Can I dispute this?',
      eligibleTitle: 'You can dispute this transaction',
      eligibleDescription:
        'If you do not recognise this charge, or something went wrong with it, we will guide you through raising a dispute.',
      notEligibleTitle: 'This transaction cannot be disputed',
      disputeButton: 'Dispute this transaction',
      viewDispute: 'View dispute',
      existingDisputeTitle: 'This transaction has a dispute',
      // Used only if the server sends no message of its own for a refusal.
      reason: {
        NOT_A_DEBIT: 'Only money that left your account can be disputed.',
        PENDING: 'This transaction is still pending. You can dispute it once it has cleared.',
        TOO_OLD: 'This transaction is too old to dispute.',
        ALREADY_DISPUTED: 'This transaction already has an open dispute.',
      },
      noReason: 'This transaction cannot be disputed.',
    },
  },
  pages: {
    transactions: {
      title: 'Transactions',
    },
    transactionDetail: {
      title: 'Transaction details',
    },
    disputeNew: {
      title: 'Raise a dispute',
      emptyTitle: 'Disputes are coming soon',
      emptyDescription:
        'You will be guided step by step: choose a reason, confirm the amount and describe what happened.',
    },
    disputes: {
      title: 'Your disputes',
      emptyTitle: 'No disputes to show yet',
      emptyDescription:
        'When you dispute a transaction, you can follow its progress here. Start from the Transactions tab.',
    },
    disputeDetail: {
      title: 'Dispute details',
      emptyTitle: 'Dispute details are on their way',
      emptyDescription: 'This page will show where your dispute stands and what happens next.',
    },
    notFound: {
      title: 'Page not found',
      emptyTitle: 'We could not find that page',
      emptyDescription: 'The link may be out of date. Head back to your transactions to carry on.',
      action: 'Go to transactions',
    },
  },
};
